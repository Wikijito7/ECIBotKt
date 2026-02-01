# Discord Integration Instructions

## Discord Library: Kord

This project uses **Kord** for Discord API interaction. Currently using a **local SNAPSHOT version** with voice encryption support.

## Core Concepts

### Bot Setup

Main bot class handles connection and event routing:

```kotlin
class Bot(
    private val kord: Kord,
    private val commandHandlerService: CommandHandlerService
) {
    suspend fun start() {
        kord.login {
            presence { playing("Music") }
        }
    }
}
```

### Command Registration

Commands are registered globally:

```kotlin
// In CommandModule.kt
kord.createGlobalChatInputCommand(
    CommandName.Radio.commandName,
    localizationService.getString(LocalizationKeys.RADIO_COMMAND_DESCRIPTION)
) {
    descriptionLocalizations = localizationService.getLocalizations(...)
    // Register subcommands
}
```

### Slash Commands

Three main types:

1. **Simple Command** (`SubCommand`):
```kotlin
class PlayCommand : SubCommand {
    override suspend fun onExecute(interaction, response) { }
}
```

2. **Group Command** (`GroupCommand`):
```kotlin
class RadioGroupCommand(...) : GroupCommand {
    override suspend fun onExecute(interaction, response) {
        // Route to appropriate subcommand based on interaction.command.name
    }
}
```

3. **Component Handler** (`Component`):
```kotlin
class RadioListCommand(...) : SubCommand, Component {
    override suspend fun onInteract(interaction) {
        // Handle button clicks
    }
}
```

## Embeds

### Creating Embeds

Basic embed structure:

```kotlin
response.respond {
    embed {
        title = "Title"
        description = "Description"
        color = Color(0x01B05B)  // Green
        
        field {
            name = "Field Name"
            value = "Field Value"
            inline = true  // For side-by-side columns
        }
        
        footer {
            text = "Footer text"
        }
    }
}
```

### Paginated Embeds

Use `createPaginatedEmbedMessage` utility for multi-page content:

```kotlin
response.respond {
    createPaginatedEmbedMessage(
        locale = locale,
        localizationService = localizationService,
        title = "Title",
        description = null,
        currentPage = 1,
        currentPageContent = pageContent,  // List<String> (columns)
        columns = 3,                       // Max 3 inline columns
        pageCount = totalPages,
        previousButtonCustomId = "prev_btn",
        nextButtonCustomId = "next_btn"
    )
}
```

### Column Layout

Discord supports maximum **3 inline fields per row**:

```kotlin
// Format data into columns
val itemsPerColumn = (items.size + columns - 1) / columns
val pageContent = items.chunked(itemsPerColumn).map { 
    it.joinToString("\n") 
}
```

### Embed Limitations

- **Fields**: Maximum 25 per embed
- **Field Name**: 256 characters
- **Field Value**: 1024 characters
- **Description**: 4096 characters
- **Title**: 256 characters

## Interactions

### Button Interactions

```kotlin
override suspend fun onInteract(interaction: ComponentInteraction) {
    val customId = (interaction as? ButtonInteraction)?.component?.customId
    
    when (customId) {
        "prev_btn" -> handlePrevious()
        "next_btn" -> handleNext()
    }
}
```

### Autocomplete

```kotlin
override suspend fun onAutocomplete(interaction: AutoCompleteInteraction) {
    val focusedOption = interaction.focusedOption
    val input = focusedOption.value
    
    // Suggest matching items
    interaction.suggest(radios.filter { it.contains(input, ignoreCase = true) })
}
```

### Message Components

Button creation in embeds:

```kotlin
components = mutableListOf(
    ActionRowBuilder().apply {
        interactionButton(
            style = ButtonStyle.Secondary,
            customId = "prev_btn"
        ) {
            label = localizationService.getString(LocalizationKeys.QUEUE_PREVIOUS_BUTTON_LABEL, locale)
            disabled = currentPage == 1
        }
    }
)
```

## Localization

Always localize user-facing content:

```kotlin
val locale = interaction.guildLocale.orDefaultLocale()

embed {
    title = localizationService.getString(
        key = LocalizationKeys.RADIO_LIST_EMBED_TITLE,
        locale = locale
    )
}
```

## Voice Channels

### Connecting

```kotlin
suspend fun connectToVoiceChannel() {
    voiceChannel.connect {
        audioProvider { audioPlayer.provide(20, TimeUnit.MILLISECONDS)?.let { 
            AudioFrame.fromData(it.data) 
        } }
    }
}
```

### Audio Playback

Uses Lavaplayer with Kord voice integration:
- Audio provided in 20ms frames
- Supports multiple sources (YouTube, SoundCloud, Spotify, Radio)
- Per-guild audio player instances

## Caching Strategy

### In-Memory Caching

For static data like country codes:

```kotlin
class RadioService {
    private var cachedCountryCodes: RadioCountryCodeDTO? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_EXPIRATION_MS = 3600000L  // 1 hour
    
    suspend fun getCountryCodes(): RemoteResponse<RadioCountryCodeDTO> {
        val currentTime = System.currentTimeMillis()
        
        // Return cached data if valid
        if (cachedCountryCodes != null && (currentTime - cacheTimestamp) < CACHE_EXPIRATION_MS) {
            return RemoteResponse.Success(cachedCountryCodes!!)
        }
        
        // Fetch and cache
        return apiCall().also {
            cachedCountryCodes = it
            cacheTimestamp = currentTime
        }
    }
}
```

**Performance**: 75x faster (150ms API → 2ms cache)

## Error Handling

All Discord operations should handle errors gracefully:

```kotlin
when (val result = service.call()) {
    is RemoteResponse.Success -> { /* Handle success */ }
    is RemoteResponse.Error -> {
        response.respond {
            content = localizationService.getString(
                key = LocalizationKeys.ERROR_MESSAGE,
                locale = locale
            )
        }
    }
}
```

## Best Practices

1. **Always localize** user-facing strings
2. **Use constants** for custom IDs (ComponentsEnum)
3. **Handle errors** with user-friendly messages
4. **Cache static data** to reduce API calls
5. **Test interactions** with mocked Discord objects
6. **Follow rate limits** - Discord has strict rate limits
7. **Use embeds** for rich formatting (not plain text when possible)

## Testing

Mock Discord interactions:

```kotlin
val interaction = mockk<ChatInputCommandInteraction> {
    every { kord } returns mockedKord
    every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
}

val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()
```

See `testing.instructions.md` for full testing patterns.
