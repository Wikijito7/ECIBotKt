# Code Quality Instructions

This document covers both **formatting standards** (ktlint) and **refactoring patterns** for maintaining high-quality code in ECIBotKt.

---

## Part 1: Code Formatting (ktlint)

### Running Ktlint

```bash
# Check for issues
./gradlew ktlintCheck

# Auto-fix issues (when possible)
./gradlew ktlintFormat
```

### Common Formatting Issues

#### 1. import-ordering

Imports must be in alphabetical order.

**Wrong**:
```kotlin
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.entity.interaction.GroupCommand
```

**Correct**:
```kotlin
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.SubCommand
```

#### 2. final-newline

Files must end with empty newline.

**Wrong**:
```kotlin
class MyClass { }
// (no newline at end)
```

**Correct**:
```kotlin
class MyClass { }
// (empty line here)
```

#### 3. blank-line-between-when-conditions

Add blank lines between multiline `when` branches.

**Wrong**:
```kotlin
when (result) {
    is Success -> {
        doSomething()
    }
    is Error -> {
        handleError()
    }
}
```

**Correct**:
```kotlin
when (result) {
    is Success -> {
        doSomething()
    }
    
    is Error -> {
        handleError()
    }
}
```

#### 4. no-trailing-spaces

Remove spaces at end of lines. Blank lines should be truly empty.

**Wrong**:
```kotlin
fun example() {
    // comment
    
    doSomething()    
}
```

**Correct**:
```kotlin
fun example() {
    // comment

    doSomething()
}
```

#### 5. no-empty-first-line-in-method-block

Don't start method with empty line.

**Wrong**:
```kotlin
fun myMethod() {
    
    doSomething()
}
```

**Correct**:
```kotlin
fun myMethod() {
    doSomething()
}
```

#### 6. no-blank-line-before-rbrace

No blank line before closing brace.

**Wrong**:
```kotlin
fun myMethod() {
    doSomething()
    
}
```

**Correct**:
```kotlin
fun myMethod() {
    doSomething()
}
```

#### 7. comment-wrapping

Block comments should be on their own lines.

**Wrong**:
```kotlin
val x = 5 /* this is a value */
```

**Correct**:
```kotlin
/* This is a value */
val x = 5
```

### IDE Integration

**IntelliJ IDEA**:
1. Install ktlint plugin
2. Enable "Reformat with ktlint on save"
3. Configure to use project's ktlint settings

**VS Code**:
1. Install "Kotlin Language" extension
2. Enable format on save

### EditorConfig

Project includes `.editorconfig`:

```ini
[*]
charset = utf-8
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true
```

### CI/CD Integration

Ktlint runs in GitHub Actions. **PRs cannot be merged if ktlint fails.**

---

## Part 2: Refactoring Patterns

### 1. Eliminating Code Duplication with Default Parameters

**Problem**: Multiple methods with similar logic.

**Before**:
```kotlin
fun loadAndPlay(url: String) {
    audioPlayerManager.loadItem(url, getAudioLoadResultHandler(url))
}

fun loadAndPlayNext(url: String) {
    audioPlayerManager.loadItem(url, getAudioLoadResultHandlerNext(url))
}

// Duplicate handlers (30+ lines each)
private fun getAudioLoadResultHandler(url: String) = object : AudioLoadResultHandler {
    override fun trackLoaded(track: AudioTrack) { onTrackLoaded(track) }
    override fun playlistLoaded(playlist: AudioPlaylist) { onPlaylistLoaded(playlist) }
}

private fun getAudioLoadResultHandlerNext(url: String) = object : AudioLoadResultHandler {
    override fun trackLoaded(track: AudioTrack) { onTrackLoadedNext(track) }
    override fun playlistLoaded(playlist: AudioPlaylist) { onPlaylistLoadedNext(playlist) }
}
```

**After**:
```kotlin
fun loadAndPlay(url: String, addToFront: Boolean = false) {
    audioPlayerManager.loadItem(url, getAudioLoadResultHandler(url, addToFront))
}

private fun getAudioLoadResultHandler(url: String, addToFront: Boolean = false) = 
    object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) { 
            onTrackLoaded(track, addToFront) 
        }
        override fun playlistLoaded(playlist: AudioPlaylist) { 
            onPlaylistLoaded(playlist, addToFront) 
        }
    }
```

**Benefits**: 50%+ code reduction, backward compatible, easier maintenance.

### 2. Search Algorithm Prioritization

**Pattern**: Exact match first, then fuzzy.

```kotlin
fun findTrack(searchTerm: String): Track? {
    val normalized = searchTerm.lowercase()
    
    // First pass: exact match
    val exactMatchIndex = queue.indexOfFirst { 
        it.getDisplayTrackName().lowercase() == normalized 
    }
    
    // Second pass: contains match (only if no exact match)
    val trackIndex = if (exactMatchIndex != -1) {
        exactMatchIndex
    } else {
        queue.indexOfFirst { 
            it.getDisplayTrackName().lowercase().contains(normalized) 
        }
    }
    
    return if (trackIndex != -1) queue[trackIndex] else null
}
```

### 3. Autocomplete Ordering Strategy

Discord autocomplete has 25-item limit. Order matters.

```kotlin
override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
    val input = interaction.command.strings[ARGUMENT_NAME].orEmpty()
    
    // First: sounds that start with input (sorted alphabetically)
    val startsWithMatches = allFiles
        .filter { it.nameWithoutExtension.startsWith(input, ignoreCase = true) }
        .sortedBy { it.nameWithoutExtension.lowercase() }
    
    // Then: sounds that contain input (sorted alphabetically)
    val containsMatches = allFiles
        .filter { 
            !it.nameWithoutExtension.startsWith(input, ignoreCase = true) &&
            it.nameWithoutExtension.contains(input, ignoreCase = true)
        }
        .sortedBy { it.nameWithoutExtension.lowercase() }
    
    // Combine: startsWith first, then contains, up to 25
    val suggestions = (startsWithMatches + containsMatches)
        .take(25)
        .map { file ->
            Choice.StringChoice(
                name = file.nameWithoutExtension.take(100),
                nameLocalizations = Optional.Missing(),
                value = file.nameWithoutExtension.take(100)
            )
        }
    
    interaction.suggest(suggestions)
}
```

### 4. Extracting Constants

**Before**:
```kotlin
val protocolEndIndex = url.indexOf("://")
val protocol = if (protocolEndIndex != -1) {
    url.take(protocolEndIndex + 3)
} else {
    "https://"
}
```

**After**:
```kotlin
private const val PROTOCOL_SEPARATOR = "://"
private const val DEFAULT_PROTOCOL = "https://"
private const val NOT_FOUND_INDEX = -1

val protocolEndIndex = url.indexOf(PROTOCOL_SEPARATOR)
val protocol = if (protocolEndIndex != NOT_FOUND_INDEX) {
    url.take(protocolEndIndex + PROTOCOL_SEPARATOR.length)
} else {
    DEFAULT_PROTOCOL
}
```

### 5. Expression Body for Simple Functions

**Before**:
```kotlin
fun String.transformUrl(): String {
    return UrlTransformer.transformMonochromeToTidal(this)
}
```

**After**:
```kotlin
fun String.transformUrl(): String = UrlTransformer.transformMonochromeToTidal(this)
```

**Rule**: Single return statement = use expression body.

### 6. Updating Tests After API Changes

When changing method signatures:

1. **Update mock declarations**:
```kotlin
justRun { loadAndPlayNext(any()) }           // Before
justRun { loadAndPlay(any(), any()) }        // After
```

2. **Update verification calls**:
```kotlin
coVerify { lavaPlayerService.loadAndPlayNext(expectedUrl) }                    // Before
coVerify { lavaPlayerService.loadAndPlay(expectedUrl, addToFront = true) }     // After
```

---

## Checklist for Code Quality

Before submitting a PR, verify:

- [ ] `./gradlew ktlintCheck` passes
- [ ] All existing tests pass
- [ ] No trailing spaces or formatting issues
- [ ] Code duplication is minimized
- [ ] Constants are extracted (no magic strings/numbers)
- [ ] Expression body used for simple functions
- [ ] Backward compatibility is maintained (if applicable)
- [ ] Documentation is updated if needed

## Anti-patterns to Avoid

1. **The Boolean Trap**: `fun process(data: Data, flag1: Boolean, flag2: Boolean)` → Use enums instead
2. **Over-generalization**: Making methods too generic → Keep focused, use composition
3. **Breaking Changes**: Changing signatures without defaults → Use default parameters

## Real Examples from ECIBotKt

**GuildLavaPlayerService**: Removed 87 lines of duplicated handlers using `addToFront` parameter → 50% code reduction

**SoundCommand Autocomplete**: Two-pass filtering (startsWith, then contains) → Better UX

## References

- ktlint: https://pinterest.github.io/ktlint/
- Kotlin Coding Conventions: https://kotlinlang.org/docs/coding-conventions.html
- Refactoring Guru: https://refactoring.guru/
- EditorConfig: https://editorconfig.org/
