# Architecture Instructions

## Project Architecture

ECIBotKt follows **Clean Architecture** with clear separation of concerns across four layers:

```
Presentation Layer (Discord Commands)
    ↓
Application Layer (Services & Use Cases)
    ↓
Domain Layer (DTOs & Business Objects)
    ↓
Infrastructure Layer (Discord API, HTTP, DI)
```

## Layer Responsibilities

### 1. Presentation Layer (`commands/`)
- **Purpose**: Handle Discord interactions (slash commands, buttons, autocomplete)
- **Pattern**: Command Pattern with interfaces
- **Key Components**:
  - `Command`: Main command interface
  - `SubCommand`: Individual subcommands
  - `GroupCommand`: Commands with nested subcommands
  - `Component`: Interactive components (buttons)
  - `Autocomplete`: Autocomplete handlers

**Example Structure**:
```kotlin
class RadioGroupCommand(...) : GroupCommand, Component, Autocomplete {
    override suspend fun onRegisterCommand(...) { }
    override suspend fun onExecute(...) { }
    override suspend fun onInteract(...) { }
    override suspend fun onAutocomplete(...) { }
}
```

### 2. Application Layer (`services/`)
- **Purpose**: Business logic and orchestration
- **Pattern**: Service classes with single responsibility
- **Key Services**:
  - `GuildLavaPlayerService`: Audio playback per guild
  - `GuildQueueService`: Queue management per guild
  - `LocalizationService`: Multi-language support
  - `RadioService`: Radio station operations
  - `ConfigService`: Configuration management

**Service Pattern**:
```kotlin
class RadioService(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) {
    suspend fun getCountryCodes(): RemoteResponse<RadioCountryCodeDTO> { }
}
```

### 3. Domain Layer (`data/`, `domain/`)
- **Purpose**: Data structures and business objects
- **Contents**:
  - DTOs for API responses
  - Business objects (TrackBO, etc.)
  - Error types and response wrappers

**Example DTO**:
```kotlin
@Serializable
@JsonIgnoreUnknownKeys
data class RadioDTO(
    @SerialName("radioName")
    val radioName: String,
    val url: String
)
```

### 4. Infrastructure Layer (`bot/`, `di/`, utils)
- **Purpose**: External integrations and utilities
- **Components**:
  - `Bot.kt`: Discord connection and event routing
  - `di/`: Koin modules for dependency injection
  - HTTP clients, logging, extensions

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

### Best Practices
- Use constructor injection
- Define interfaces for testability
- Group related dependencies in modules
- Use `single` for stateful services, `factory` for stateless

## Key Patterns

### 1. Guild Isolation
Each Discord server (guild) has isolated state:
```kotlin
class GuildQueueService {
    private val guildPlayers = mutableMapOf<Snowflake, GuildLavaPlayerService>()
    
    fun getOrCreateLavaPlayerService(interaction): GuildLavaPlayerService {
        return guildPlayers.getOrPut(interaction.guildId) { ... }
    }
}
```

### 2. RemoteResponse Wrapper
All service methods return `RemoteResponse<T>`:
```kotlin
sealed class RemoteResponse<out T> {
    data class Success<T>(val data: T) : RemoteResponse<T>()
    data class Error<T>(val error: ErrorType) : RemoteResponse<T>()
}
```

### 3. Localization
All user-facing strings use localization:
```kotlin
localizationService.getString(
    key = LocalizationKeys.RADIO_PLAY_FOUND,
    locale = interaction.guildLocale.orDefaultLocale()
)
```

## Project Structure

```
src/main/kotlin/es/wokis/
├── bot/
│   └── Bot.kt                    # Main Discord bot setup
├── commands/
│   ├── Command.kt                # Base interfaces
│   ├── CommandName.kt            # Command name constants
│   ├── ComponentsEnum.kt         # Component ID constants
│   ├── commons/                  # Shared utilities
│   ├── lavaplayer/              # Audio commands
│   ├── player/                  # Player controls
│   ├── play/                    # Play command
│   ├── queue/                   # Queue management
│   ├── radio/                   # Radio commands
│   │   ├── RadioGroupCommand.kt
│   │   ├── RadioUtils.kt
│   │   └── subcommands/
│   ├── skip/                    # Skip track
│   └── tts/                     # Text-to-speech
├── data/
│   ├── error/
│   ├── radio/
│   └── response/
├── di/
│   ├── BotModule.kt
│   ├── CommandModule.kt
│   ├── DomainModule.kt
│   ├── RemoteModule.kt
│   └── ServicesModule.kt
├── domain/
├── services/
│   ├── commands/
│   ├── config/
│   ├── lavaplayer/
│   ├── localization/
│   ├── queue/
│   └── radio/
└── utils/
```

## State Management

### Per-Guild State
- Stored in memory (maps with guild ID as key)
- Cleared on bot restart
- Examples: Player state, queue, current track

### Configuration
- JSON files in `src/main/resources/`
- Loaded at startup via `ConfigService`
- Environment-specific (debug vs production)

## Extension Functions

Add functionality to existing classes:
```kotlin
// Extension on Discord types
fun ChatInputCommandInteraction.getGuildId(): Snowflake = ...

// Extension on business objects
fun TrackBO.getDisplayTrackName(): String = ...
```

## Critical Rules

1. **Never mix layers**: Presentation code shouldn't access infrastructure directly
2. **Use DI**: Don't create dependencies manually, inject them
3. **Handle errors**: Always use RemoteResponse wrapper for service calls
4. **Localize everything**: User-facing strings must use localization
5. **Test services**: Business logic must have unit tests
