---
name: code-quality
description: CRITICAL: Load for ALL code changes. Defines ktlint rules and refactoring patterns for ECIBotKt. Covers import ordering, constants extraction, expression body, autocomplete prioritization. Violations = failing CI. Must follow before ANY commit.
---

## When to use me
- When writing or reviewing Kotlin code for formatting compliance
- When refactoring duplicated code or extracting constants
- When implementing autocomplete with proper prioritization

## Not intended for
- Running build/test/lint gates → use `quality-check`
- Testing patterns → use `testing`
- Architecture patterns → use `architecture`

---

## Running Ktlint

```bash
# Check for issues
./gradlew ktlintCheck

# Auto-fix issues
./gradlew ktlintFormat
```

**PRs cannot be merged if ktlint fails.**

---

## Ktlint Rules

### 1. Import Ordering

Alphabetical order within groups.

**Wrong:**
```kotlin
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.entity.interaction.GroupCommand
```

**Correct:**
```kotlin
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.SubCommand
```

### 2. Final Newline

Files must end with empty newline.

### 3. Blank Lines Between When Conditions

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

### 4. No Trailing Spaces

Remove spaces at end of lines. Blank lines should be truly empty.

### 5. No Empty First Line in Method Block

```kotlin
fun myMethod() {
    doSomething()  // No blank line here
}
```

### 6. No Blank Line Before Closing Brace

```kotlin
fun myMethod() {
    doSomething()
}  // No blank line before this
```

### 7. Comment Wrapping

Block comments on their own lines:

```kotlin
/* This is a value */
val x = 5
```

Not: `val x = 5 /* this is a value */`

---

## EditorConfig

```ini
[*]
charset = utf-8
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true
```

---

## Refactoring Patterns

### 1. Eliminate Duplication with Default Parameters

**Before** (duplicate handlers):
```kotlin
fun loadAndPlay(url: String) {
    audioPlayerManager.loadItem(url, getAudioLoadResultHandler(url))
}
fun loadAndPlayNext(url: String) {
    audioPlayerManager.loadItem(url, getAudioLoadResultHandlerNext(url))
}
```

**After** (50%+ code reduction):
```kotlin
fun loadAndPlay(url: String, addToFront: Boolean = false) {
    audioPlayerManager.loadItem(url, getAudioLoadResultHandler(url, addToFront))
}
```

### 2. Search Algorithm Prioritization

Exact match first, then fuzzy:

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

Discord has 25-item limit. Order matters:

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

**Before:**
```kotlin
val protocolEndIndex = url.indexOf("://")
val protocol = if (protocolEndIndex != -1) {
    url.take(protocolEndIndex + 3)
} else {
    "https://"
}
```

**After:**
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

**Before:**
```kotlin
fun String.transformUrl(): String {
    return UrlTransformer.transformMonochromeToTidal(this)
}
```

**After:**
```kotlin
fun String.transformUrl(): String = UrlTransformer.transformMonochromeToTidal(this)
```

**Rule:** Single return statement = use expression body.

---

## Updating Tests After API Changes

When changing method signatures:

```kotlin
// Update mock declarations
justRun { loadAndPlayNext(any()) }           // Before
justRun { loadAndPlay(any(), any()) }        // After

// Update verification calls
coVerify { lavaPlayerService.loadAndPlayNext(expectedUrl) }                    // Before
coVerify { lavaPlayerService.loadAndPlay(expectedUrl, addToFront = true) }     // After
```

---

## PR Checklist

Before submitting:
- [ ] `./gradlew ktlintCheck` passes
- [ ] All existing tests pass
- [ ] No trailing spaces or formatting issues
- [ ] Code duplication is minimized
- [ ] Constants extracted (no magic strings/numbers)
- [ ] Expression body used for simple functions
- [ ] Backward compatibility maintained (if applicable)

---

## Anti-patterns to Avoid

1. **The Boolean Trap**: `fun process(data: Data, flag1: Boolean, flag2: Boolean)` → Use enums
2. **Over-generalization**: Making methods too generic → Keep focused, use composition
3. **Breaking Changes**: Changing signatures without defaults → Use default parameters

---

## References
- `.github/instructions/code-quality.instructions.md` — full context
- ktlint: https://pinterest.github.io/ktlint/
- Kotlin Coding Conventions: https://kotlinlang.org/docs/coding-conventions.html
