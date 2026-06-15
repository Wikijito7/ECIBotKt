# ECIBotKt - AGENTS.md

## Project Overview

**ECIBotKt** is a Discord bot written in Kotlin (JVM application) that provides music playback, text-to-speech, internet radio, and custom sound features. It's a rewrite of the original Python ECIBot with improved architecture and type safety.

### Key Information
- **Language**: Kotlin 2.1.21
- **Build Tool**: Gradle with Kotlin DSL
- **JDK**: Java 17
- **Architecture**: Clean Architecture with Koin DI
- **Discord Library**: Kord (currently using local SNAPSHOT JARs for voice encryption support)

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
- See `dependencies` skill for details

#### In-Memory Caching
Country codes are cached for 1 hour to reduce API calls:
- 75x performance improvement (150ms → 2ms)
- Cache clears on bot restart
- See `discord-integration` skill for implementation

### Code Quality
- **detekt**: Strict code style enforcement
- **Jacoco**: Code coverage reporting
- **SonarCloud**: Quality gates in CI/CD
- All code must pass detekt checks before merge

### Testing Strategy
- **Framework**: Kotlin Test with Mockk
- **Pattern**: Mock external dependencies (Discord API, HTTP clients)
- **CI**: Tests run on every PR via GitHub Actions

## Skills Reference

Skills in `.agents/skills/` provide workflow-level guidance. Load them when working on related tasks:

| Skill | When to Load |
|-------|-------------|
| `architecture` | Adding new commands, services, or features |
| `code-quality` | ALL code changes (detekt compliance) |
| `dependencies` | Adding/updating dependencies or troubleshooting class errors |
| `discord-integration` | Adding new Discord commands (8-step checklist) |
| `localization` | Adding user-facing strings or new languages |
| `quality-check` | BEFORE opening any PR (build + detekt + tests + coverage gates) |
| `testing` | Writing tests or debugging test failures |

## Working with This Project

### Before Starting
1. Read `.memory/STATUS.md` for current project state
2. Load the appropriate skill for your task from `.agents/skills/`
3. Verify Java 17 is installed
4. Ensure Discord bot token is configured

### Common Tasks
- **Build**: `./gradlew build`
- **Test**: `./gradlew test`
- **Detekt**: `./gradlew detekt`
- **Coverage**: `./gradlew jacocoTestReport`

### Adding New Features
1. Load `architecture` and `discord-integration` skills
2. Follow clean architecture patterns
3. Use Koin for dependency injection
4. Add localization keys for all user-facing strings (load `localization` skill)
5. Write tests for new functionality (load `testing` skill)
6. Ensure detekt compliance (load `code-quality` skill)
7. Run quality gates before PR (load `quality-check` skill)

## Important Notes

- **NOT an Android project** - This is a JVM console application despite being in AndroidStudioProjects directory
- Uses SNAPSHOT Kord version for voice encryption support
- Audio features require voice channel permissions
- Guild-specific state is isolated by guild ID
- Configuration via JSON files in `src/main/resources/`

## Memory Bank

See `.memory/` for past learnings and project state:
- `.memory/STATUS.md` — current project state
- `.memory/learning/` — historical learnings and decisions
