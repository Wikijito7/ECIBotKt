# Code Quality Instructions

This document covers both **formatting standards** (enforced by Detekt and its formatting ruleset, which wraps ktlint rules) and **refactoring patterns** for maintaining high-quality code in ECIBotKt.

---

## Part 1: Code Formatting (Detekt)

### Running Detekt

```bash
# Run the blocking quality gate across the root project and every module
./gradlew detektBudget
```

Detekt is configured in `config/detekt/detekt.yml`, with the `detekt-formatting` ruleset
(ktlint-backed formatting rules) applied to the root project and to every subproject that applies
the Detekt plugin. The scope is discovered from the build instead of a maintained module list, so a
module is covered as soon as it is Detekt-enabled. The enabled set is therefore an observation, not
an invariant: at the time of writing it is the **root project** plus `:core`, `:configuration`,
`:locale-persistence`, `:player` and `:jda-runtime` — adding a Detekt-enabled module extends the
gate automatically, with no document edit required. The gate has **no baseline file**, no
`ignoreFailures`, and no broad source exclusions.

Detekt's `build.maxIssues = 40` is evaluated **per Gradle project**, so it alone cannot enforce a
repository-wide ceiling: a run could pass with up to 40 findings in each scanned project. The
user-requested cap is repository-wide, so the blocking task is `detektBudget`:
it depends on the `detekt` task of every scanned project, reads their XML reports, and fails when
the **combined** finding count exceeds 40. `check`/`build` depend on it, and the `Check Code
Quality` workflow runs it. `./gradlew detekt` still produces the per-project reports, but
`detektBudget` is the gate that enforces the global budget.

Fix findings first; do not add a baseline, exclude source, raise either limit, or disable a rule to
fit the threshold. Keep the ceiling at the user-requested 40; do not lower it to the current
finding count.

Autocorrection is disabled (`autoCorrect = false`): Detekt reports problems but never rewrites
sources. Fix findings by hand or with your IDE. The separate `spikes/jda-dave` Gradle build is
outside the root project graph and is not scanned; Gradle build scripts (`*.kts`) are not part of
Detekt's Kotlin source sets either.

The experimental `detektMain`/`detektTest` type-resolution tasks are not part of the gate, so do not
use their failures as a quality signal.

### Common Formatting Issues

The rule ids below are the underlying ktlint rules surfaced by the Detekt formatting ruleset.

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
1. Install the Detekt plugin
2. Point it at `config/detekt/detekt.yml`
3. Enable analysis on save (formatting findings are not autocorrected by the build)

### EditorConfig

The formatting ruleset still reads `.editorconfig`, so ktlint rule toggles there keep informing the
Detekt formatting rules. The project currently uses:

```ini
[*.{kt,kts}]
ktlint_experimental = disabled
ktlint_code_style = intellij_idea
ktlint_standard_trailing-comma-on-call-site = disabled
ktlint_standard_trailing-comma-on-declaration-site = disabled
ktlint_standard_function-signature = disabled
ktlint_standard_multiline-expression-wrapping = disabled
ktlint_standard_no-wildcard-imports = disabled
ktlint_standard_class-signature = disabled
```

### CI/CD Integration

Detekt runs in GitHub Actions (`.github/workflows/detekt.yaml`) as the `Check Code Quality` job,
triggered on `pull_request` — the same trigger as the ktlint workflow it replaces — and the
aggregate `detektBudget` task is also wired into Gradle `check`/`build`, so CI and local build runs
enforce the same budget. **PRs cannot be merged if Detekt or the repository-wide finding budget
fails.** The job runs `./gradlew detektBudget` and publishes the HTML, Markdown, XML and SARIF
reports as the `detekt-reports` artifact. The SonarCloud workflow also runs Detekt through
`./gradlew build`.

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

- [ ] `./gradlew detektBudget` passes
- [ ] New findings are fixed rather than baselined or suppressed
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

- Detekt: https://detekt.dev/docs/introduction/configurations
- ktlint (formatting rules used by the Detekt formatting ruleset): https://pinterest.github.io/ktlint/
- Kotlin Coding Conventions: https://kotlinlang.org/docs/coding-conventions.html
- Refactoring Guru: https://refactoring.guru/
- EditorConfig: https://editorconfig.org/
