---
name: write-tests
description: Load when writing or fixing tests for ECIBotKt. Covers MockK patterns, command/service/HTTP test conventions, interaction mocking, and what not to mock. Only relevant for this project's Kotlin + Kord + Ktor stack.
---

# Write Tests Skill — ECIBotKt

## Frameworks
- **Test runner**: Kotlin Test (`kotlin.test.Test`) + JUnit 5 (`org.junit.jupiter.api.Test`)
- **Mocking**: MockK (`io.mockk.*`)
- **Coroutines**: `kotlinx.coroutines.test.runTest`
- **Assertions**: JUnit 5 Assertions (`org.junit.jupiter.api.Assertions.*`) or direct `assertTrue`/`assertEquals` from Kotlin test
- **HTTP mocking**: Ktor MockEngine via `mock.getMockedHttpClient`

## Test Structure

### Package naming
Tests mirror the source package under `src/test/kotlin/`:
```
src/main/kotlin/commands/tts/TTSCommand.kt → src/test/kotlin/commands/tts/TTSCommandTest.kt
src/main/kotlin/services/radio/RadioService.kt → src/test/kotlin/services/radio/RadioServiceTest.kt
```

### Class template
```kotlin
package <some>.package

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test  // or org.junit.jupiter.api.Test

class FooTest {

    // Dependencies: mockk() each
    private val dep1 = mockk<Dep1>()

    // SUT
    private val sut = Foo(dep1)

    @Test
    fun `Given state When action Then expected result`() = runTest {
        // Given — arrange mocks
        coEvery { dep1.something(any()) } returns "value"
        coJustRun { dep1.voidMethod(any()) }

        // When
        val result = sut.doSomething()

        // Then — verify
        assertEquals("expected", result)
        coVerify(exactly = 1) { dep1.something("arg") }
        coVerify(exactly = 0) { dep1.otherMethod(any()) }
    }
}
```

## MockK Patterns

### Standard mock
```kotlin
val service = mockk<MyService>()
coEvery { service.method(any()) } returns "result"
```

### Relaxed mock (for complex objects where you don't care about mock setup)
```kotlin
val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
```

### Chained mock config (for config objects)
```kotlin
val configService = mockk<ConfigService> {
    every { config } returns mockk {
        every { ask } returns mockk {
            every { enabled } returns true
        }
    }
}
```

### Void / Unit returning methods
```kotlin
coJustRun { service.voidMethod(any()) }
```

### Throwing
```kotlin
coEvery { service.method(any()) } throws IllegalStateException()
```

### vararg matcher
```kotlin
coEvery { service.method(any(), *anyVararg()) } returns "value"
```

## HTTP Mocking
Use the existing helper at `src/test/kotlin/mock/HttpClientMock.kt`:
```kotlin
import mock.getMockedHttpClient

val httpClient = getMockedHttpClient("""{"key": "value"}""")
val service = MyService(httpClient)
```
This creates a Ktor `HttpClient` with `MockEngine` that always responds with the given JSON content.

## Command Test Patterns

### `onExecute` — verify downstream service calls
**Don't mock `respond` or `edit` (inline extension functions)** — use `relaxed = true`:
```kotlin
val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
coEvery { localizationService.getString(any(), any(), any()) } returns "message"
coJustRun { service.loadAndPlayMessage(any(), any()) }

sut.onExecute(interaction, response)

coVerify(exactly = 1) { service.loadAndPlayMessage(any(), "expected arg") }
```

### Error cases — verify service was NOT called
```kotlin
sut.onExecute(interaction, response)
coVerify(exactly = 0) { service.loadAndPlayMessage(any(), any()) }
```

### `onRegisterCommand` — currently **@Ignored** due to MockK issues
See `TTSCommandTest.kt` for reference. Don't add new `onRegisterCommand` tests.

## Interaction Mocking

### ChatInputCommandInteraction
```kotlin
val interaction = mockk<ChatInputCommandInteraction> {
    every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
    every { data } returns mockk {
        every { guildId.value } returns Snowflake(123)
    }
    every { command } returns mockk {
        every { strings } returns mapOf("argName" to "value")
    }
}
```

### MessageCommandInteraction
```kotlin
val targetMessage = mockk<Message> {
    every { content } returns "message content"
}
val interaction = mockk<MessageCommandInteraction> {
    every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
    every { data } returns mockk {
        every { guildId.value } returns Snowflake(123)
    }
    coEvery { getTarget() } returns targetMessage
}
```

## Key Imports
```kotlin
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.MessageCommandInteraction
import dev.kord.core.entity.Message
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
```

## Don't
- ❌ Mock inline extension functions (`respond`, `edit`, `deferPublicResponse`)
- ❌ Write `onRegisterCommand` tests (MockK can't handle the builder pattern)
- ❌ Use `@Test` from JUnit 5 and Kotlin test in the same file (pick one)
- ❌ Import unused Kord entities
