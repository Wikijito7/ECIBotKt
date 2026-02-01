# Testing Instructions

## Testing Framework

This project uses:
- **Kotlin Test** (JUnit 5 platform)
- **Mockk** for mocking
- **Kotlinx Coroutines Test** for coroutine testing

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

## Writing Tests

### Basic Test Structure

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

### Mocking Discord Interactions

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
```

### Mocking Services

```kotlin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

private val radioService: RadioService = mockk()
private val localizationService: LocalizationService = mockk()

// Setup mocks
@BeforeEach
fun setup() {
    coEvery { 
        radioService.getCountryCodes() 
    } returns RemoteResponse.Success(data)
    
    every { 
        localizationService.getString(any(), any()) 
    } returns "Localized Text"
}
```

### Testing Coroutines

All async tests must use `runTest`:

```kotlin
@Test
fun `test async operation`() = runTest {
    // Test code here
    service.asyncMethod()
}
```

### Testing Commands

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

### Testing Services

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

## Mocking Patterns

### Mockk Functions

| Function | Use For |
|----------|---------|
| `every { }` | Regular (non-suspend) functions |
| `coEvery { }` | Suspend functions |
| `verify { }` | Verify regular calls |
| `coVerify { }` | Verify suspend calls |
| `mockk()` | Create mock objects |
| `mockk<T>(relaxed = true)` | Create mock with default returns |

### Common Mocks

**ConfigService**:
```kotlin
val configService: ConfigService = mockk {
    every { config } returns mockk {
        every { debug } returns false
    }
}
```

**LocalizationService**:
```kotlin
every { 
    localizationService.getString(any(), any()) 
} returns "Localized String"

every { 
    localizationService.getStringFormat(any(), any(), *anyVararg()) 
} returns "Formatted String"
```

## Test Naming Conventions

Use descriptive names following the pattern:
```
Given [context] When [action] Then [expected result]
```

Examples:
- `Given empty queue When skip Then show error`
- `Given valid radio name When play Then add to queue`
- `Given error response When search Then show error message`

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "RadioCountryCodesCommandTest"

# Run with pattern
./gradlew test --tests "*Radio*"

# Run with coverage report
./gradlew jacocoTestReport

# Check coverage
open build/reports/jacoco/test/html/index.html
```

## Coverage Requirements

- **Minimum**: 80% line coverage for new code
- **Target**: 100% coverage for critical paths
- **Exceptions**: Discord API wrappers (hard to test)

## Test Categories

### Unit Tests
- Test individual classes in isolation
- Mock all dependencies
- Fast execution (< 100ms each)

### Integration Tests
- Test service interactions
- Mock external APIs only
- Test data flow through multiple layers

## Best Practices

1. **Test behavior, not implementation** - Don't test private methods
2. **One assertion per test** (ideally) - Tests should verify one thing
3. **Use descriptive names** - Test name should explain what's being tested
4. **Mock external dependencies** - Don't call real Discord API or databases
5. **Test both success and failure paths**
6. **Keep tests independent** - Don't rely on test execution order
7. **Use `runTest` for coroutines** - Always use for suspend functions
8. **Verify interactions** - Use `coVerify` to ensure methods were called

## Common Test Scenarios

### Testing Error Handling

```kotlin
@Test
fun `Given error response When execute Then show error`() = runTest {
    // Given
    coEvery { service.method() } returns RemoteResponse.Error(...)
    
    // When
    command.onExecute(interaction, response)
    
    // Then
    coVerify { 
        localizationService.getString(LocalizationKeys.ERROR_KEY, any())
    }
}
```

### Testing Localization

```kotlin
@Test
fun `Given spanish locale When execute Then use spanish text`() = runTest {
    // Given
    every { interaction.guildLocale } returns Locale.SPANISH
    
    // When
    command.onExecute(interaction, response)
    
    // Then
    verify { 
        localizationService.getString(any(), Locale.SPANISH)
    }
}
```

### Testing Empty Data

```kotlin
@Test
fun `Given empty list When display Then show empty message`() = runTest {
    // Given
    coEvery { service.getList() } returns RemoteResponse.Success(emptyList())
    
    // When
    command.onExecute(interaction, response)
    
    // Then
    verify { 
        localizationService.getString(LocalizationKeys.EMPTY_MESSAGE, any())
    }
}
```

## Troubleshooting

### Test fails with "No answer found"
**Cause**: Missing mock for suspend function
**Fix**: Use `coEvery` instead of `every`

### Test fails with "verify was not called"
**Cause**: Verification on wrong thread
**Fix**: Use `coVerify` for suspend functions

### Test hangs indefinitely
**Cause**: Coroutine not completing
**Fix**: Ensure all coroutines complete within `runTest`

## References

- Mockk documentation: https://mockk.io/
- Kotlin coroutines testing: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
- JUnit 5: https://junit.org/junit5/docs/current/user-guide/
