---
name: architecture
description: IMPORTANT: Load when adding new commands, services, or features. Defines the Clean Architecture patterns used in ECIBotKt. Covers Koin DI, Command Pattern, guild isolation, and RemoteResponse wrapper. Must read before touching any code.
---

## When to use me
- When adding new commands, services, or features
- When understanding how DI modules are organized
- When working with guild-specific state or RemoteResponse

## Not intended for
- Code quality checks → use `code-quality`
- Testing patterns → use `testing`
- Discord API specifics (embeds, voice) → use `discord-integration`

---

## Architecture Layers

```
Presentation (commands/) → Application (services/) → Domain (data/, domain/) → Infrastructure (bot/, di/, utils)
```

### 1. Presentation — `commands/`
- Interfaces: `Command`, `SubCommand`, `GroupCommand`, `Component`, `Autocomplete`
- Handle Discord interactions only — no business logic

```kotlin
class RadioGroupCommand(...) : GroupCommand, Component, Autocomplete {
    override suspend fun onRegisterCommand(...) { }
    override suspend fun onExecute(interaction, response) { }
    override suspend fun onInteract(interaction) { }
    override suspend fun onAutoComplete(interaction) { }
}
```

### 2. Application — `services/`
- Single-responsibility service classes
- Return `RemoteResponse<T>` for all operations

```kotlin
class RadioService(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) {
    suspend fun getCountryCodes(): RemoteResponse<RadioCountryCodeDTO> { }
}
```

### 3. Domain — `data/`, `domain/`
- DTOs with `@Serializable`, `@JsonIgnoreUnknownKeys`
- Business objects (TrackBO, etc.)
- Error types and response wrappers

### 4. Infrastructure — `bot/`, `di/`, `utils/`
- `Bot.kt`: Discord connection and event routing
- Koin modules for DI
- HTTP clients, logging, extensions

---

## Dependency Injection (Koin)

### Module Organization
```kotlin
// BotModule.kt
single { Bot(get(), get()) }

// ServicesModule.kt
single { RadioService(get(), get()) }

// CommandModule.kt
single { RadioGroupCommand(get(), get(), ...) }
```

### Rules
- Constructor injection only
- `single` for stateful services, `factory` for stateless
- Group related dependencies in modules
- Define interfaces for testability

---

## Guild Isolation Pattern

Each Discord server has isolated state via map with guild ID as key:

```kotlin
class GuildQueueService {
    private val guildPlayers = mutableMapOf<Snowflake, GuildLavaPlayerService>()

    fun getOrCreateLavaPlayerService(interaction): GuildLavaPlayerService {
        return guildPlayers.getOrPut(interaction.guildId) { ... }
    }
}
```

---

## RemoteResponse Wrapper

All service methods return `RemoteResponse<T>`:

```kotlin
sealed class RemoteResponse<out T> {
    data class Success<T>(val data: T) : RemoteResponse<T>()
    data class Error<T>(val error: ErrorType) : RemoteResponse<T>()
}
```

Handle in commands:
```kotlin
when (val result = service.call()) {
    is RemoteResponse.Success -> { /* Handle success */ }
    is RemoteResponse.Error -> { /* Show localized error */ }
}
```

---

## Extension Functions

Add functionality to existing classes:
```kotlin
// Extension on Discord types
fun ChatInputCommandInteraction.getGuildId(): Snowflake = ...

// Extension on business objects
fun TrackBO.getDisplayTrackName(): String = ...
```

---

## Critical Rules

1. **Never mix layers**: Presentation code shouldn't access infrastructure directly
2. **Use DI**: Don't create dependencies manually, inject them
3. **Handle errors**: Always use RemoteResponse wrapper for service calls
4. **Localize everything**: User-facing strings must use localization
5. **Test services**: Business logic must have unit tests

---

## References
- `.github/instructions/architecture.instructions.md` — full context
