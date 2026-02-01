# Code Style Instructions

## Overview

This project uses **ktlint** for enforcing Kotlin coding standards automatically.

## Running Ktlint

```bash
# Check for issues
./gradlew ktlintCheck

# Auto-fix issues (when possible)
./gradlew ktlintFormat
```

## Common Issues and Fixes

### 1. import-ordering

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

### 2. final-newline

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

### 3. blank-line-between-when-conditions

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

### 4. no-trailing-spaces

Remove spaces at end of lines.

**Wrong**:
```kotlin
val x = 5    
```

**Correct**:
```kotlin
val x = 5
```

### 5. no-empty-first-line-in-method-block

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

### 6. no-blank-line-before-rbrace

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

### 7. comment-wrapping

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

## IDE Integration

### IntelliJ IDEA

1. Install ktlint plugin
2. Enable "Reformat with ktlint on save"
3. Configure to use project's ktlint settings

### VS Code

1. Install "Kotlin Language" extension
2. Enable format on save

## EditorConfig

Project includes `.editorconfig` for consistent formatting:

```ini
[*]
charset = utf-8
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true
```

## CI/CD Integration

Ktlint runs in GitHub Actions:

```yaml
- name: Run ktlint
  run: ./gradlew ktlintCheck
```

**Critical**: PRs cannot be merged if ktlint fails.

## Best Practices

1. **Run ktlintCheck before committing**
2. **Fix issues immediately** - Don't let them accumulate
3. **Use ktlintFormat** for auto-fixable issues
4. **Configure IDE** to show violations in real-time
5. **Follow existing patterns** - Look at similar files

## Configuration

Ktlint is configured in `build.gradle.kts`:

```kotlin
ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}
```

## Common Exclusions

Some files are excluded from ktlint:
- Generated code
- Build output
- Test resources

## Troubleshooting

### ktlintCheck fails in CI but passes locally
**Cause**: Different ktlint versions
**Fix**: Run `./gradlew --stop` then retry

### Can't auto-fix an issue
**Solution**: Some issues require manual fixing

### Too many issues to fix at once
**Solution**: Fix incrementally, one file at a time

## References

- ktlint documentation: https://pinterest.github.io/ktlint/
- EditorConfig: https://editorconfig.org/
