package architecture

/**
 * A single Kotlin file-header import declaration.
 *
 * [segments] is the normalized imported name: backticks are removed from every
 * identifier segment, the alias is not part of the segments, and a trailing
 * wildcard is preserved as the `"*"` segment.
 */
data class KotlinImport(
    val statement: String,
    val segments: List<String>,
    val alias: String?,
    val line: Int,
) {
    val wildcard: Boolean get() = segments.lastOrNull() == "*"

    fun startsWithSegments(prefix: List<String>): Boolean {
        if (segments.size < prefix.size) return false
        return prefix.indices.all { segments[it] == prefix[it] }
    }
}

data class KotlinFileHeader(
    val packageName: String?,
    val packageSegments: List<String>,
    val imports: List<KotlinImport>,
)

/**
 * Small deterministic lexer for the Kotlin file header.
 *
 * Only real import declarations found in code state inside the header are
 * returned. Import-looking text in line comments, block/nested block comments,
 * regular strings, character literals and raw strings is ignored. Header
 * scanning stops at the first top-level declaration.
 *
 * This is an import-declaration scanner only: it does not detect fully-qualified
 * vendor references used without an `import`, reflection string targets, or
 * vendor types in compiled signatures.
 */
object KotlinFileHeaderParser {

    fun parse(source: String): KotlinFileHeader = HeaderLexer(source).parse()
}

private class HeaderLexer(private val source: String) {

    private var index: Int = 0

    fun parse(): KotlinFileHeader {
        if (source.startsWith('\uFEFF')) {
            index = 1
        }
        skipShebang()
        skipTrivia()

        while (isAtFileAnnotation()) {
            skipFileAnnotation()
            skipTrivia()
        }

        var packageName: String? = null
        var packageSegments: List<String> = emptyList()
        if (isKeyword("package")) {
            advanceBy("package".length)
            val body = readDirectiveBody()
            packageSegments = splitSegments(body)
            packageName = packageSegments.joinToString(".")
            skipTrivia()
        }

        val imports = mutableListOf<KotlinImport>()
        while (isKeyword("import")) {
            val importLine = lineAt(index)
            advanceBy("import".length)
            val body = readDirectiveBody()
            imports += parseImportBody(body, importLine)
            skipTrivia()
        }

        return KotlinFileHeader(packageName, packageSegments, imports)
    }

    private fun skipShebang() {
        if (source.startsWith("#!", index)) {
            advanceTo(skipLineCommentAt(index))
        }
    }

    private fun skipTrivia() {
        advanceTo(skipTriviaAt(index))
    }

    private fun skipTriviaAt(position: Int): Int {
        var pos = position
        while (pos < source.length) {
            when {
                source[pos].isWhitespace() -> pos++
                source.startsWith("//", pos) -> pos = skipLineCommentAt(pos)
                source.startsWith("/*", pos) -> pos = skipBlockCommentAt(pos)
                else -> return pos
            }
        }
        return pos
    }

    private fun skipLineCommentAt(position: Int): Int {
        var pos = position
        while (pos < source.length && source[pos] != '\n' && source[pos] != '\r') {
            pos++
        }
        return pos
    }

    private fun skipBlockCommentAt(position: Int): Int {
        var pos = position + 2
        var depth = 1
        while (pos < source.length && depth > 0) {
            when {
                source.startsWith("/*", pos) -> {
                    depth++
                    pos += 2
                }

                source.startsWith("*/", pos) -> {
                    depth--
                    pos += 2
                }

                else -> pos++
            }
        }
        return pos
    }

    private fun isKeyword(word: String): Boolean {
        if (!source.startsWith(word, index)) return false
        val end = index + word.length
        return end >= source.length || !isIdentifierPart(source[end])
    }

    private fun isAtFileAnnotation(): Boolean {
        if (index >= source.length || source[index] != '@') return false
        var pos = index + 1
        if (!source.startsWith("file", pos)) return false
        pos += "file".length
        if (pos < source.length && isIdentifierPart(source[pos])) return false
        pos = skipTriviaAt(pos)
        return pos < source.length && source[pos] == ':'
    }

    private fun skipFileAnnotation() {
        advanceBy(1) // '@'
        advanceBy("file".length)
        skipTrivia()
        if (index < source.length && source[index] == ':') {
            advanceBy(1)
        }
        skipTrivia()

        while (index < source.length && isAnnotationNamePart(source[index])) {
            advanceBy(1)
        }
        skipTrivia()

        if (index < source.length && source[index] in OPEN_DELIMITERS) {
            advanceTo(consumeBalancedAt(index))
        }
    }

    private fun consumeBalancedAt(position: Int): Int {
        val expectedClosers = ArrayDeque<Char>()
        var pos = position
        while (pos < source.length) {
            when {
                source.startsWith("//", pos) -> {
                    pos = skipLineCommentAt(pos)
                    continue
                }

                source.startsWith("/*", pos) -> {
                    pos = skipBlockCommentAt(pos)
                    continue
                }

                source.startsWith(RAW_STRING_DELIMITER, pos) -> {
                    pos = skipRawStringAt(pos)
                    continue
                }

                source[pos] == '"' -> {
                    pos = skipStringAt(pos)
                    continue
                }

                source[pos] == '\'' -> {
                    pos = skipCharAt(pos)
                    continue
                }

                else -> {
                    val current = source[pos]
                    when (current) {
                        '(', '[', '{' -> {
                            expectedClosers.addLast(closingDelimiterFor(current))
                            pos++
                        }

                        ')', ']', '}' -> {
                            if (expectedClosers.isNotEmpty() && expectedClosers.last() == current) {
                                expectedClosers.removeLast()
                            }
                            pos++
                            if (expectedClosers.isEmpty()) return pos
                        }

                        else -> pos++
                    }
                }
            }
        }
        return pos
    }

    private fun readDirectiveBody(): String {
        val body = StringBuilder()
        var inBacktick = false
        while (index < source.length) {
            when {
                !inBacktick && source.startsWith("//", index) -> {
                    advanceTo(skipLineCommentAt(index))
                    break
                }

                !inBacktick && source.startsWith("/*", index) -> {
                    advanceTo(skipBlockCommentAt(index))
                    body.append(' ')
                }

                source[index] == '`' -> {
                    inBacktick = !inBacktick
                    body.append(source[index])
                    advanceBy(1)
                }

                source[index] == '\n' || source[index] == '\r' -> {
                    advanceBy(1)
                    break
                }

                !inBacktick && source[index] == ';' -> {
                    // A semicolon terminates this header statement outside backtick identifiers;
                    // the caller continues with the next statement.
                    advanceBy(1)
                    break
                }

                else -> {
                    body.append(source[index])
                    advanceBy(1)
                }
            }
        }
        return body.toString()
    }

    private fun parseImportBody(body: String, importLine: Int): KotlinImport {
        val statement = body.trim()
        val aliasIndex = findAliasSeparator(statement)
        val target = if (aliasIndex >= 0) statement.substring(0, aliasIndex).trim() else statement
        val alias = if (aliasIndex >= 0) {
            statement.substring(aliasIndex + "as".length).trim().trim('`')
        } else {
            null
        }
        return KotlinImport(
            statement = statement,
            segments = splitSegments(target),
            alias = alias?.takeIf { it.isNotEmpty() },
            line = importLine,
        )
    }

    private fun findAliasSeparator(statement: String): Int {
        var inBacktick = false
        var position = 0
        while (position < statement.length) {
            val current = statement[position]
            when {
                current == '`' -> {
                    inBacktick = !inBacktick
                    position++
                }

                !inBacktick && current.isWhitespace() -> {
                    var candidate = position
                    while (candidate < statement.length && statement[candidate].isWhitespace()) {
                        candidate++
                    }
                    val afterAs = candidate + "as".length
                    if (statement.startsWith("as", candidate) &&
                        (afterAs >= statement.length || !isIdentifierPart(statement[afterAs]))
                    ) {
                        return candidate
                    }
                    position++
                }

                else -> position++
            }
        }
        return -1
    }

    private fun splitSegments(raw: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var inBacktick = false
        raw.trim().forEach { character ->
            when {
                character == '`' -> inBacktick = !inBacktick

                !inBacktick && character == '.' -> {
                    addSegment(segments, current)
                    current.clear()
                }

                else -> current.append(character)
            }
        }
        addSegment(segments, current)
        return segments
    }

    private fun addSegment(segments: MutableList<String>, current: StringBuilder) {
        val segment = current.toString().trim()
        if (segment.isNotEmpty()) {
            segments += segment
        }
    }

    private fun skipRawStringAt(position: Int): Int {
        val end = source.indexOf(RAW_STRING_DELIMITER, position + RAW_STRING_DELIMITER.length)
        return if (end < 0) source.length else end + RAW_STRING_DELIMITER.length
    }

    private fun skipStringAt(position: Int): Int {
        var pos = position + 1
        while (pos < source.length) {
            when (source[pos]) {
                '\\' -> pos += 2
                '"' -> return pos + 1
                '\n', '\r' -> return pos
                else -> pos++
            }
        }
        return pos
    }

    private fun skipCharAt(position: Int): Int {
        var pos = position + 1
        while (pos < source.length) {
            when (source[pos]) {
                '\\' -> pos += 2
                '\'' -> return pos + 1
                '\n', '\r' -> return pos
                else -> pos++
            }
        }
        return pos
    }

    private fun advanceBy(amount: Int) {
        advanceTo(index + amount)
    }

    private fun advanceTo(newIndex: Int) {
        index = newIndex.coerceAtMost(source.length)
    }

    /**
     * Counts LF, CRLF and isolated CR terminators as one line break each.
     * Computed on demand so mixed line endings cannot skew the reported import line.
     */
    private fun lineAt(position: Int): Int {
        var line = 1
        var pos = 0
        val limit = position.coerceAtMost(source.length)
        while (pos < limit) {
            when (source[pos]) {
                '\r' -> {
                    line++
                    if (pos + 1 < limit && source[pos + 1] == '\n') {
                        pos++
                    }
                }

                '\n' -> line++
            }
            pos++
        }
        return line
    }

    private companion object {
        const val RAW_STRING_DELIMITER = "\"\"\""
        val OPEN_DELIMITERS = setOf('(', '[', '{')

        fun isIdentifierPart(character: Char): Boolean =
            character.isLetterOrDigit() || character == '_'

        fun isAnnotationNamePart(character: Char): Boolean =
            !character.isWhitespace() && character !in OPEN_DELIMITERS && character != ')' &&
                character != ']' && character != '}' && character != '/'

        fun closingDelimiterFor(opener: Char): Char = when (opener) {
            '(' -> ')'
            '[' -> ']'
            else -> '}'
        }
    }
}
