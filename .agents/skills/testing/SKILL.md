---
name: testing
description: IMPORTANT: Load when writing tests or debugging test failures. Defines testing conventions for ECIBotKt. Covers MockK patterns, BDD naming, coroutine testing with runTest, and Discord interaction mocking. All logic changes REQUIRE tests.
---

## When to use me
- When writing unit tests for commands, services, or data classes
- When mocking Discord interactions or services
- When debugging test failures (MockK, coroutine issues)

## Not intended for
- Code quality checks → use `code-quality`
- Build/test gates → use `quality-check`
- Architecture patterns → use `architecture`

---

## Test Frameworks

- **Kotlin Test** (JUnit 5 platform)
- **Mockk** (1.14.2) for mocking
- **Kotlinx Coroutines Test** for async testing

---

## Test Structure

```
src/test/kotlin/
├── commands/
│   ├── lavaplayer/
│   ├── play/
│   ├── player/
│   ├── queue/
│   ├── radio/
│   └── ...
├── data/
├── services/
│   ├── lavaplayer/
│   ├── localization/
│   ├── queue/
│   └── radio/
└── utils/
```

---

## BDD Test Naming

Pattern: `Given [context] When [action] Then [expected result]`

```kotlin
@Test
fun `Given empty queue When skip Then show error`() = runTest {
    // Given
    // When
    // Then
}
```

Examples:
- `Given valid radio name When play Then add to queue`
- `Given error response When search Then show error message`
- `Given spanish locale When execute Then use spanish text`

---

## Basic Test Structure

```kotlin
package commands.radio.subcommands

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RadioCountryCodesCommandTest {

    @Test
    fun `Given X When Y Then Z`() = runTest {
        // Given - Setup mocks and test data

        // When - Execute the action

        // Then - Verify results
    }
}
```

---

## MockK Patterns

### Creating Mocks

```kotlin
val radioService: RadioService = mockk()
val localizationService: LocalizationService = mockk()
```

### Regular Functions

```kotlin
every {
    localizationService.getString(any(), any())
} returns "Localized Text"
```

### Suspend Functions

```kotlin
coEvery {
    radioService.getCountryCodes()
} returns RemoteResponse.Success(data)
```

### Verifying Calls

```kotlin
// Regular
verify(exactly = 1) { mock.method() }

// Suspend
coVerify(exactly = 1) { mock.suspendMethod() }
```

### Allow Unit Returns

```kotlin
justRun { command.onRegisterCommand(any()) }
```

### Reference Table

| Function | Use For |
|----------|---------|
| `every { }` | Regular (non-suspend) functions |
| `coEvery { }` | Suspend functions |
| `verify { }` | Verify regular calls |
| `coVerify { }` | Verify suspend calls |
| `justRun { }` | Allow Unit return suspend/regular calls |
| `mockk()` | Create mock objects |
| `mockk(relaxed = true)` | Create mock with default returns (avoid) |

---

## Mocking Discord Interactions

```kotlin
import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import io.mockk.every
import io.mockk.mockk
import mock.mockedKord

val interaction = mockk<ChatInputCommandInteraction> {
    every { kord } returns mockedKord
    every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
    every { id } returns Snowflake(123)
}

val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()
```

---

## Mocking Common Services

### ConfigService

```kotlin
val configService: ConfigService = mockk {
    every { config } returns mockk {
        every { debug } returns false
    }
}
```

### LocalizationService

```kotlin
every {
    localizationService.getString(any(), any())
} returns "Localized String"

every {
    localizationService.getStringFormat(any(), any(), *anyVararg())
} returns "Formatted String"
```

---

## Testing Commands

```kotlin
@Test
fun `Given valid input When execute Then success`() = runTest {
    // Given
    val command = RadioCountryCodesCommand(radioService, localizationService)

    // When
    command.onExecute(interaction, mockedResponse)

    // Then
    coVerify(exactly = 1) {
        radioService.getCountryCodes()
    }
}
```

---

## Testing Error Handling

```kotlin
@Test
fun `Given error response When execute Then show error`() = runTest {
    // Given
    coEvery { service.method() } returns RemoteResponse.Error(ErrorType(...))

    // When
    command.onExecute(interaction, response)

    // Then
    coVerify {
        localizationService.getString(LocalizationKeys.ERROR_KEY, any())
    }
}
```

---

## Testing Services

Mock HTTP client for service tests:

```kotlin
@Test
fun `Given valid response When getCountryCodes Then return success`() = runTest {
    // Given
    val jsonResponse = """{"countryCode": ["US", "ES"]}"""
    val httpClient = getMockedHttpClient(jsonResponse)
    val service = RadioService(httpClient, configService)

    // When
    val result = service.getCountryCodes()

    // Then
    assertTrue(result is RemoteResponse.Success)
}
```

---

## Running Tests

```bash
# All tests
./gradlew test

# Single test class
./gradlew test --tests "RadioCountryCodesCommandTest"

# With pattern
./gradlew test --tests "*Radio*"

# Coverage report
./gradlew jacocoTestReport
```

---

## Coverage Requirements

- **Minimum**: 80% line coverage for new code
- **Target**: 100% for critical paths
- **Exceptions**: Discord API wrappers (hard to test)

---

## Golden Rules

1. **Test behavior, not implementation** — Don't test private methods
2. **One assertion per test** (ideally)
3. **Use descriptive names** — `Given...When...Then...`
4. **Mock external dependencies** — Don't call real Discord API
5. **Test both success and failure paths**
6. **Keep tests independent** — Don't rely on execution order
7. **Use `runTest` for coroutines** — Always for suspend functions
8. **Verify interactions** — Use `coVerify` to ensure methods were called
9. **NEVER use `lateinit var`** — Create instances inline as `val`
10. **ALWAYS specify verification count** — `verify(exactly = n)`

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| "No answer found" | Missing mock for suspend function | Use `coEvery` instead of `every` |
| "verify was not called" | Verification on wrong thread | Use `coVerify` for suspend functions |
| Test hangs | Coroutine not completing | Ensure all coroutines complete within `runTest` |

---

## References
- `.github/instructions/testing.instructions.md` — full context
- Mockk: https://mockk.io/
- Coroutines testing: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
