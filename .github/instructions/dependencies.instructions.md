# Dependencies Instructions

## Dependency Management

This project uses **Gradle with Kotlin DSL** for dependency management.

## Version Catalog

Dependencies are defined in `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.1.21"
ktor = "3.1.3"
koin = "4.0.2"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
koin-core = { module = "io.insert-koin:koin-core" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

### Usage in build.gradle.kts
```kotlin
dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.koin.core)
}
```

## Special Case: Local JAR Dependencies

### When to Use
- SNAPSHOT versions not in Maven Central
- Custom-built libraries
- Pre-release features (like Kord voice encryption)

### Setup

1. **Copy JARs to `libs/` folder**:
```bash
mkdir -p libs
cp /path/to/jars/*.jar libs/
```

2. **Configure repository in build.gradle.kts**:
```kotlin
repositories {
    flatDir {
        dirs("libs")
    }
    mavenLocal()  // Keep for transitive dependencies
    mavenCentral()
}
```

3. **Add dependency**:
```kotlin
dependencies {
    implementation(fileTree("libs") { include("kord-*.jar") })
}
```

### Transitive Dependencies

**Critical**: `flatDir` doesn't resolve transitive dependencies. You must manually add all required dependencies.

**Example** (Kord requires these):
```kotlin
// Transitive deps required by local Kord JARs
implementation(libs.kotlinx.datetime)        // kotlinx-datetime
implementation(libs.kord.cache)             // kord-cache-api
implementation(libs.kord.cache.map)         // kord-cache-map
implementation(libs.ktor.client.okhttp)     // ktor-client-okhttp
```

### Version Catalog for Transitive Deps

Add to `gradle/libs.versions.toml`:
```toml
[versions]
# TODO: Remove when Kord merges voice encryption to main
kord-cache = "0.5.4"
kotlinx-datetime = "0.6.1"

[libraries]
kord-cache = { module = "dev.kord.cache:cache-api", version.ref = "kord-cache" }
kord-cache-map = { module = "dev.kord.cache:cache-map", version.ref = "kord-cache" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

## Key Dependencies

### Discord Integration
- **Kord**: Discord API wrapper (currently local SNAPSHOT)
- **Kord sub-modules**: core, voice, rest, gateway, common

### HTTP & Networking
- **Ktor**: HTTP client framework
  - Client core, CIO engine, logging
  - Content negotiation, JSON serialization
  - OkHttp engine (required by Kord)

### Audio Processing
- **Lavaplayer**: Audio playback (2.2.6)
- **Lavaplayer-youtube**: YouTube support
- **Lavaplayer-lavasrc**: Additional sources (Spotify, Deezer)

### Dependency Injection
- **Koin**: Lightweight DI (4.0.2)
  - Use BOM for version management
  - Core module only

### Serialization
- **Kotlinx Serialization**: JSON (1.8.1)

### Testing
- **Mockk**: Mocking framework (1.14.2)
- **Kotlinx Coroutines Test**: Coroutine testing
- **JUnit Jupiter**: Test framework

### Logging
- **SLF4J Simple**: Logging implementation

## Build Commands

```bash
# Refresh dependencies
./gradlew --refresh-dependencies

# Check dependency tree
./gradlew dependencies

# Build project
./gradlew build

# Create fat JAR
./gradlew fatJar
```

## Troubleshooting

### Missing transitive dependency
```
java.lang.NoClassDefFoundError: kotlinx/datetime/Instant
```
**Fix**: Add the missing dependency manually to build.gradle.kts

### Class not found at runtime
```
java.lang.ClassNotFoundException: io.ktor.client.engine.okhttp.OkHttp
```
**Fix**: Add ktor-client-okhttp dependency

### Version conflicts
Check dependency tree:
```bash
./gradlew dependencies --configuration runtimeClasspath
```

## Best Practices

1. **Use version catalog** for all dependencies
2. **Document temporary dependencies** with TODO comments
3. **Keep JARs out of git** (add to .gitignore if needed)
4. **Test after dependency changes** - run full test suite
5. **Update regularly** - check for new stable versions
6. **Pin versions** - avoid using `+` or dynamic versions

## Migration from Local to Maven

When Kord releases voice encryption support:

1. Remove JARs from `libs/`
2. Remove `flatDir` repository
3. Uncomment Kord entries in version catalog
4. Remove manual transitive dependencies
5. Update to stable version in version catalog
6. Run tests to verify
