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

Adding a new Discord command to ECIBotKt requires registration in **multiple places**. Missing any step will cause the command to not work.

#### Complete Checklist for New Commands

When adding a command like `/sound`, you MUST complete ALL these steps:

**1. Command Name (CommandName.kt)**

Add the command name to the sealed class:

```kotlin
sealed class CommandName(val commandName: String) {
    data object Play : CommandName("play")
    data object Sound : CommandName("sound")  // <-- ADD THIS
    data object Queue : CommandName("queue")
    // ...
}
```

**2. Localization Keys (LocalizationKeys.kt)**

Add keys for command description and arguments:

```kotlin
object LocalizationKeys {
    const val SOUND_COMMAND_DESCRIPTION = "sound_command_description"
    const val SOUND_COMMAND_INPUT_DESCRIPTION = "sound_command_input_description"
    // ...
}
```

**3. Localization Strings**

Add translations in both language files:

**lang.yml** (English):
```yaml
sound_command_description: Plays a local sound by name
sound_command_input_description: Name of the local sound to play
```

**lang_es-ES.yml** (Spanish):
```yaml
sound_command_description: Reproduce un sonido local por nombre
sound_command_input_description: Nombre del sonido local a reproducir
```

**4. Command Implementation**

Create the command class in appropriate package:

```kotlin
package es.wokis.commands.sound

class SoundCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command, Autocomplete {  // Implement Autocomplete if needed
    
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        // Register command with Discord
    }
    
    override suspend fun onExecute(interaction, response) {
        // Handle command execution
    }
    
    override suspend fun onAutoComplete(interaction) {
        // Handle autocomplete (optional)
    }
}
```

**5. Dependency Injection (CommandModule.kt)**

Register the command with Koin:

```kotlin
val commandModule = module {
    factoryOf(::PlayCommand)
    factoryOf(::SoundCommand)  // <-- ADD THIS
    factoryOf(::QueueCommand)
    // ...
}
```

**6. CRITICAL: CommandHandlerService (CommandHandlerService.kt)**

**This is the MOST COMMONLY FORGOTTEN step!**

You must add your command in **FOUR places**:

**a) Import the command**
```kotlin
import es.wokis.commands.sound.SoundCommand
```

**b) Add to constructor**
```kotlin
class CommandHandlerServiceImpl(
    private val playCommand: PlayCommand,
    private val soundCommand: SoundCommand,  // <-- ADD THIS
    private val queueCommand: QueueCommand,
    // ...
) : CommandHandlerService {
```

**c) Register in `onRegisterSimpleCommand()`**
```kotlin
override fun onRegisterSimpleCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
    playCommand.onRegisterCommand(commandBuilder)
    soundCommand.onRegisterCommand(commandBuilder)  // <-- ADD THIS
    queueCommand.onRegisterCommand(commandBuilder)
    // ...
}
```

**d) Handle execution in `onExecute()`**
```kotlin
override suspend fun onExecute(interaction, response) {
    when (val commandName = interaction.command.rootName) {
        CommandName.Play.commandName -> playCommand.onExecute(interaction, response)
        CommandName.Sound.commandName -> soundCommand.onExecute(interaction, response)  // <-- ADD THIS
        CommandName.Queue.commandName -> queueCommand.onExecute(interaction, response)
        // ...
    }
}
```

**e) Handle autocomplete in `onAutocomplete()` (if applicable)**
```kotlin
override suspend fun onAutocomplete(interaction: AutoCompleteInteraction) {
    when (val commandName = interaction.command.rootName) {
        CommandName.Sound.commandName -> soundCommand.onAutoComplete(interaction)  // <-- ADD THIS
        CommandName.Radio.commandName -> radioGroupCommand.onAutoComplete(interaction)
    }
}
```

**7. Update Tests (CommandHandlerServiceTest.kt)**

Add the command to tests:

```kotlin
class CommandHandlerServiceTest {
    private val playCommand: PlayCommand = mockk()
    private val soundCommand: SoundCommand = mockk()  // <-- ADD THIS
    // ...
    
    private val commandHandlerService = CommandHandlerServiceImpl(
        playCommand = playCommand,
        soundCommand = soundCommand,  // <-- ADD THIS
        // ...
    )
    
    @Test
    fun `When onRegisterSimpleCommand is called Then register all commands`() {
        // Given
        justRun { soundCommand.onRegisterCommand(any()) }  // <-- ADD THIS
        // ...
        
        // Then
        verify(exactly = 1) {
            soundCommand.onRegisterCommand(commandBuilder)  // <-- ADD THIS
            // ...
        }
    }
}
```

**8. Create Command Tests**

Create comprehensive tests in `src/test/kotlin/commands/<command>/`:

```kotlin
class SoundCommandTest {
    // Test: Command registration
    // Test: Command execution
    // Test: Autocomplete
    // Test: Error handling
}
```

#### Pattern: Separating Command Concerns

When you have similar functionality (like playing audio), consider separating concerns:

**Example: `/play` vs `/sound`**

- **`/play`** - External content (URLs, search, mixes)
  - YouTube URLs
  - Spotify links
  - Search queries ("never gonna give you up")
  - Mixes and playlists

- **`/sound`** - Local content only
  - Files from `./audio/` folder
  - Known sound names
  - Autocomplete support
  - Custom display names (no "Unknown Artist")

**Benefits:**
1. **Better UX**: Autocomplete for local sounds, direct input for URLs
2. **Clear separation**: `/play` for external, `/sound` for internal
3. **Easier maintenance**: Different logic for different sources
4. **Better error handling**: Different messages for "file not found" vs "search failed"

#### Critical Reminders

**Always verify CommandHandlerService has ALL registrations**
- Missing constructor parameter = compile error
- Missing `onRegisterSimpleCommand()` = command won't appear in Discord
- Missing `onExecute()` case = "Unknown command" error
- Missing `onAutocomplete()` = autocomplete won't work

**Test with real Discord server**
- Commands may appear in code but not in Discord if registration is incomplete
- Use Discord's "Refresh Commands" if testing locally

**Follow the pattern**
- Look at existing commands (PlayCommand, QueueCommand) as examples
- Copy the structure, change the details
- Consistency makes it easier for other monkes!

#### Troubleshooting

**Command doesn't appear in Discord**
- Check `onRegisterSimpleCommand()` has your command
- Verify CommandName matches command name exactly
- Check Discord permissions and bot token

**"Unknown command" error**
- Missing case in `onExecute()` switch statement
- Check CommandName spelling matches

**Autocomplete doesn't work**
- Missing `onAutoComplete()` implementation
- Missing case in `onAutocomplete()`
- Check `autocomplete = true` in argument definition

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
