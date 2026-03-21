# ECIBotKt - Copilot Instructions

## Project Overview

**ECIBotKt** is a Discord bot written in Kotlin (JVM application) that provides music playback, text-to-speech, internet radio, and custom sound features. It's a rewrite of the original Python ECIBot with improved architecture and type safety.

### Key Information
- **Language**: Kotlin 2.1.21
- **Build Tool**: Gradle with Kotlin DSL
- **JDK**: Java 17
- **Architecture**: Clean Architecture with Koin DI
- **Discord Library**: Kord (currently using local SNAPSHOT JARs for voice encryption support)
- **Test Coverage**: 148 tests, all passing

### Main Features
- **Music**: YouTube, SoundCloud, Spotify via Lavaplayer
- **TTS**: Text-to-speech with multiple voices (FloweryTTS)
- **Radio**: Internet radio stations with search and country filtering
- **Custom Sounds**: Upload and play custom sound clips
- **Queue Management**: Per-guild isolated queues
- **Multi-language**: English and Spanish support

## Technical Summary

### Architecture Patterns
- **Dependency Injection**: Koin for lightweight, Kotlin-native DI
- **Command Pattern**: Discord slash commands implemented via interfaces (`Command`, `SubCommand`, `GroupCommand`)
- **Repository/Service Pattern**: Clear separation between data access and business logic
- **Clean Architecture**: Presentation → Application → Domain → Infrastructure layers

### Project Structure
```
src/main/kotlin/es/wokis/
├── bot/           # Discord bot setup and event handling
├── commands/      # Slash commands (organized by feature)
├── data/          # DTOs and data classes
├── di/            # Koin modules
├── domain/        # Use cases
├── services/      # Business logic (lavaplayer, queue, localization)
└── utils/         # Extension functions and helpers
```

### Key Libraries
- **Kord**: Discord API wrapper (SNAPSHOT version with voice encryption)
- **Ktor**: HTTP client for API calls
- **Lavaplayer**: Audio playback and streaming
- **Koin**: Dependency injection
- **Kotlinx Serialization**: JSON serialization
- **Mockk**: Testing framework

### Special Configurations

#### Local JAR Dependencies
Kord is loaded from `libs/` folder due to using a SNAPSHOT version with voice encryption:
- 12 JAR files in `libs/` directory
- Transitive dependencies manually configured: `kotlinx-datetime`, `kord-cache`, `ktor-client-okhttp`
- See `dependencies.instructions.md` for details

#### In-Memory Caching
Country codes are cached for 1 hour to reduce API calls:
- 75x performance improvement (150ms → 2ms)
- Cache clears on bot restart
- See `discord-integration.instructions.md` for implementation

### Code Quality
- **detekt**: Strict code style enforcement
- **Jacoco**: Code coverage reporting
- **SonarCloud**: Quality gates in CI/CD
- All code must pass detekt checks before merge

### Testing Strategy
- **Framework**: Kotlin Test with Mockk
- **Pattern**: Mock external dependencies (Discord API, HTTP clients)
- **Coverage**: 148 tests covering main functionality
- **CI**: Tests run on every PR via GitHub Actions

## Working with This Project

### Before Starting
1. Read `.memory/STATUS.md` for current project state
2. Check `.github/instructions/` for topic-specific guidance
3. Verify Java 17 is installed
4. Ensure Discord bot token is configured

### Common Tasks
- **Build**: `./gradlew build`
- **Test**: `./gradlew test`
- **Detekt**: `./gradlew detekt`
- **Coverage**: `./gradlew jacocoTestReport`

### Adding New Features
1. Follow clean architecture patterns
2. Use Koin for dependency injection
3. Add localization keys for all user-facing strings
4. Write tests for new functionality
5. Ensure detekt compliance

## Related Documentation

- **Architecture**: `.github/instructions/architecture.instructions.md`
- **Dependencies**: `.github/instructions/dependencies.instructions.md`
- **Discord Integration**: `.github/instructions/discord-integration.instructions.md`
- **Testing**: `.github/instructions/testing.instructions.md`
- **Localization**: `.github/instructions/localization.instructions.md`
- **Code Style**: `.github/instructions/code-style.instructions.md`

## Important Notes

- **NOT an Android project** - This is a JVM console application despite being in AndroidStudioProjects directory
- Uses SNAPSHOT Kord version for voice encryption support
- Audio features require voice channel permissions
- Guild-specific state is isolated by guild ID
- Configuration via JSON files in `src/main/resources/`
