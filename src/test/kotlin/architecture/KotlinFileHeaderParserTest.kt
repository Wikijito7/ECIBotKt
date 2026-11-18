package architecture

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinFileHeaderParserTest {

    @Test
    fun `Given BOM file annotation and package When parse Then real header import is reported`() {
        val source = "\uFEFF@file:Suppress(\"unused\")\npackage demo\nimport ok.Type\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
    }

    @Test
    fun `Given fake imports in line block and nested block comments When parse Then no imports are reported`() {
        val source = "// import net.dv8tion.jda.api.JDA\n" +
            "/* outer import net.dv8tion.jda.api.JDA\n" +
            "   /* nested import dev.kord.core.Kord */\n" +
            "   still import moe.kyokobot.libdave.DaveSession\n" +
            "*/\n" +
            "package demo\n" +
            "import ok.Type\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
    }

    @Test
    fun `Given fake imports in escaped regular string When parse Then no imports are reported`() {
        // Given - the fixture string spells backslash + n, not a real line break
        val source = "@file:Suppress(\"import net.dv8tion.jda.api.JDA\\nimport dev.kord.core.Kord\")\n" +
            "package demo\n" +
            "import ok.Type\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
    }

    @Test
    fun `Given fake imports in raw triple quoted string When parse Then no imports are reported`() {
        val source = "@file:Suppress(\"\"\"import net.dv8tion.jda.api.JDA\n" +
            "import dev.kord.core.Kord\"\"\")\n" +
            "package demo\n" +
            "import ok.Type\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
    }

    @Test
    fun `Given escaped character literals in file annotation When parse Then following import is reported`() {
        // Given - the fixture is scanner input; the annotation need not resolve at compile time
        val escapedQuote = "'\\''"
        val escapedBackslash = "'\\\\'"
        val source = "@file:Suppress($escapedQuote, $escapedBackslash)\n" +
            "package demo\n" +
            "import dev.kord.core.Kord\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("dev.kord.core.Kord"), header.imports.map { it.statement })
    }

    @Test
    fun `Given delimiter text in comments and strings inside annotation When parse Then header state survives`() {
        val source = "@file:Suppress(\")]}\", /* ) ] } */ \"ok\")\n" +
            "package demo\n" +
            "import net.dv8tion.jda.api.JDA\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("net.dv8tion.jda.api.JDA"), header.imports.map { it.statement })
    }

    @Test
    fun `Given nested annotation delimiters When parse Then following import is reported`() {
        val source = "@file:Foo(Bar(Baz()), [1, 2], mapOf(\"a\" to { \"b\" }))\n" +
            "package demo\n" +
            "import net.dv8tion.jda.api.JDA\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("net.dv8tion.jda.api.JDA"), header.imports.map { it.statement })
    }

    @Test
    fun `Given comments between package and imports When parse Then all real imports are found`() {
        val source = "package demo\n" +
            "// import dev.kord.core.Kord\n" +
            "/* block import net.dv8tion.jda.api.JDA */\n" +
            "import ok.One\n" +
            "\n" +
            "import ok.Two\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.One", "ok.Two"), header.imports.map { it.statement })
    }

    @Test
    fun `Given a top level declaration When fake import text follows Then header scanning stops`() {
        val source = "package demo\n" +
            "import ok.One\n" +
            "\n" +
            "class Fake {\n" +
            "    private val text = \"import net.dv8tion.jda.api.JDA\"\n" +
            "}\n" +
            "import net.dv8tion.jda.api.JDA\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.One"), header.imports.map { it.statement })
    }

    @Test
    fun `Given aliased wildcard and backticked imports When parse Then names are normalized`() {
        val source = "@file:JvmName(\"Demo\")\n" +
            "package demo\n" +
            "import net.dv8tion.jda.api.*\n" +
            "import dev.`kord`.core.SomeType as Alias\n" +
            "import moe.kyokobot.libdave.DaveSession\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(3, header.imports.size)
        val wildcard = header.imports[0]
        assertEquals(listOf("net", "dv8tion", "jda", "api", "*"), wildcard.segments)
        assertTrue(wildcard.wildcard)
        assertNull(wildcard.alias)

        val aliased = header.imports[1]
        assertEquals(listOf("dev", "kord", "core", "SomeType"), aliased.segments)
        assertEquals("Alias", aliased.alias)
        assertFalse(aliased.wildcard)
    }

    @Test
    fun `Given CR-only line endings and a header comment When parse Then package and import are read with accurate line numbers`() {
        val source = "// import net.dv8tion.jda.api.JDA\rpackage demo\rimport ok.Type\r"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
        assertEquals(3, header.imports.single().line)
    }

    @Test
    fun `Given CR-only line endings and a comment between package and import When parse Then the comment cannot swallow the import`() {
        val source = "package demo\r// import net.dv8tion.jda.api.JDA\rimport ok.Type\r"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
        assertEquals(3, header.imports.single().line)
    }

    @Test
    fun `Given CRLF line endings and a header comment When parse Then package and import are read with accurate line numbers`() {
        val source = "// import net.dv8tion.jda.api.JDA\r\npackage demo\r\nimport ok.Type\r\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
        assertEquals(3, header.imports.single().line)
    }

    @Test
    fun `Given CRLF and an escaped backslash n inside a string When parse Then it is not a source newline`() {
        // Given - the fixture spells backslash + n, not a line break
        val source = "@file:Suppress(\"import net.dv8tion.jda.api.JDA\\nimport dev.kord.core.Kord\")\r\n" +
            "package demo\r\n" +
            "import ok.Type\r\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
        assertEquals(3, header.imports.single().line)
    }

    @Test
    fun `Given shebang before package When parse Then header import line numbers remain accurate`() {
        val source = "#!/usr/bin/env kotlin\npackage demo\nimport ok.Type\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
        assertEquals(3, header.imports.single().line)
    }

    @Test
    fun `Given BOM and shebang before package When parse Then header import line numbers remain accurate`() {
        val source = "\uFEFF#!/usr/bin/env kotlin\r\npackage demo\r\nimport ok.Type\r\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("ok.Type"), header.imports.map { it.statement })
        assertEquals(3, header.imports.single().line)
    }

    @Test
    fun `Given a semicolon inside a backtick identifier When parse Then the directive is not split`() {
        val source = "import dev.`kord;legacy`.core.Type; import net.dv8tion.jda.api.JDA\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(2, header.imports.size)
        val backticked = header.imports[0]
        assertEquals("dev.`kord;legacy`.core.Type", backticked.statement)
        assertEquals(listOf("dev", "kord;legacy", "core", "Type"), backticked.segments)
        assertEquals(listOf("net", "dv8tion", "jda", "api", "JDA"), header.imports[1].segments)
    }

    @Test
    fun `Given semicolon separated package and imports When parse Then every header import is found`() {
        val source = "package demo; import ok.One; import net.dv8tion.jda.api.JDA\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("ok.One", "net.dv8tion.jda.api.JDA"), header.imports.map { it.statement })
        assertEquals(listOf(1, 1), header.imports.map { it.line })
    }

    @Test
    fun `Given semicolon separated imports across lines When parse Then source line numbers remain accurate`() {
        val source = "import ok.One; import net.dv8tion.jda.api.JDA\n" +
            "import moe.kyokobot.libdave.DaveSession\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals(
            listOf("ok.One", "net.dv8tion.jda.api.JDA", "moe.kyokobot.libdave.DaveSession"),
            header.imports.map { it.statement },
        )
        assertEquals(listOf(1, 1, 2), header.imports.map { it.line })
    }

    @Test
    fun `Given a file without imports When parse Then package is read and imports are empty`() {
        val source = "package demo\n\nobject Thing\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("demo", header.packageName)
        assertEquals(listOf("demo"), header.packageSegments)
        assertTrue(header.imports.isEmpty())
    }

    @Test
    fun `Given package segments with backticks When parse Then package is normalized`() {
        val source = "package es.`wokis`.demo\nimport ok.Type\n"

        val header = KotlinFileHeaderParser.parse(source)

        assertEquals("es.wokis.demo", header.packageName)
        assertEquals(listOf("es", "wokis", "demo"), header.packageSegments)
    }
}
