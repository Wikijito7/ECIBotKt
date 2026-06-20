---
name: write-tests
description: "CRITICAL: Load when writing tests for ECIBotKt. Covers MockK patterns, command/service/HTTP mocking, interaction mocks, and pitfalls specific to Kord + Ktor + MockK. Missing this = flaky tests and wasted time."
---

## When to use me
- After implementing a new command, service, or provider — write tests for it.
- When fixing a bug — add a test that reproduces the issue first.
- When CI is failing due to test compilation or coverage drops.

## Not intended for
- Running tests → use `./gradlew test` directly.
- Debugging test logic → use IntelliJ debugger.

---

## Test structure

Tests mirror source under `src/test/kotlin/`. File name ends with `Test.kt`.

```kotlin
package commands.something

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FooTest {
    private val dep1 = mockk<Dep1>()
    private val sut = Foo(dep1)

    @Test
    fun `Given state When action Then expected`() = runTest {
        coEvery { dep1.method(any()) } returns "value"
        coJustRun { dep1.voidMethod(any()) }

        val result = sut.doSomething()

        assertEquals("expected", result)
        coVerify(exactly = 1) { dep1.method("arg") }
        coVerify(exactly = 0) { dep1.otherMethod(any()) }
    }
}
```

---

## MockK patterns

| Pattern | Syntax |
|---|---|
| Standard mock | `val svc = mockk<MyService>()`, `coEvery { svc.foo(any()) } returns "x"` |
| Relaxed mock | `mockk<ComplexType>(relaxed = true)` — for `respond`/`edit` (inline exts) |
| Chained config | `mockk<ConfigService> { every { config } returns mockk { every { field } returns value } }` |
| Void method | `coJustRun { svc.voidMethod(any()) }` |
| Throw | `coEvery { svc.method(any()) } throws IllegalStateException()` |
| vararg | `coEvery { svc.method(any(), *anyVararg()) }` |

---

## HTTP mocking

Use `mock.getMockedHttpClient` — it returns a Ktor `HttpClient` with `MockEngine`:

```kotlin
import mock.getMockedHttpClient

val httpClient = getMockedHttpClient("""{"key": "value"}""")
val svc = MyService(httpClient)
```

---

## Command test patterns

**Don't mock inline extension functions** (`respond`, `edit`, `deferPublicResponse`). Use `relaxed = true`:

```kotlin
val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)

// Verify downstream service calls instead
coVerify(exactly = 1) { someService.method("expected arg") }

// For error cases, verify NOT called
coVerify(exactly = 0) { someService.method(any(), any()) }
```

`onRegisterCommand` tests are `@Ignore`'d — don't add new ones.

---

## Interaction mocks

### ChatInputCommandInteraction
```kotlin
val interaction = mockk<ChatInputCommandInteraction> {
    every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
    every { data } returns mockk { every { guildId.value } returns Snowflake(123) }
    every { command } returns mockk { every { strings } returns mapOf("arg" to "value") }
}
```

### MessageCommandInteraction
```kotlin
val message = mockk<Message> { every { content } returns "text" }
val interaction = mockk<MessageCommandInteraction> {
    every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
    every { data } returns mockk { every { guildId.value } returns Snowflake(123) }
    coEvery { getTarget() } returns message
}
```

---

## Don't
- Mock inline extension functions (`respond`, `edit`, `deferPublicResponse`)
- Write `onRegisterCommand` tests (MockK can't handle the builder)
- Mix JUnit 5 `@Test` and Kotlin test `@Test` in the same file
- Import unused Kord entities
