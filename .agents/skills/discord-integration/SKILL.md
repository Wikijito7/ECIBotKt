---
name: discord-integration
description: CRITICAL: Load when adding new Discord commands. Contains the 8-step command registration checklist — missing ANY step breaks the command. Covers Kord, embeds, voice, and caching. Failure to follow = broken commands.
---

## When to use me
- When adding new Discord slash commands
- When working with embeds, buttons, or autocomplete
- When handling voice channels or audio playback
- When implementing caching for API calls

## Not intended for
- Code quality checks → use `code-quality`
- Testing Discord interactions → use `testing`
- Architecture patterns → use `architecture`

---

## CRITICAL: Command Registration Checklist

Adding a new command requires registration in **8 places**. Missing any step breaks the command.

### Step 1: Command Name (`CommandName.kt`)

```kotlin
sealed class CommandName(val commandName: String) {
    data object Play : CommandName("play")
    data object Sound : CommandName("sound")  // ADD HERE
    data object Queue : CommandName("queue")
}
```

### Step 2: Localization Keys (`LocalizationKeys.kt`)

```kotlin
object LocalizationKeys {
    const val SOUND_COMMAND_DESCRIPTION = "sound_command_description"
    const val SOUND_COMMAND_INPUT_DESCRIPTION = "sound_command_input_description"
}
```

### Step 3: Localization Strings

**`lang.yml`** (English):
```yaml
sound_command_description: Plays a local sound by name
sound_command_input_description: Name of the local sound to play
```

**`lang_es-ES.yml`** (Spanish):
```yaml
sound_command_description: Reproduce un sonido local por nombre
sound_command_input_description: Nombre del sonido local a reproducir
```

### Step 4: Command Implementation

```kotlin
class SoundCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command, Autocomplete {

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

### Step 5: DI Registration (`CommandModule.kt`)

```kotlin
val commandModule = module {
    factoryOf(::PlayCommand)
    factoryOf(::SoundCommand)  // ADD HERE
    factoryOf(::QueueCommand)
}
```

### Step 6: CommandHandlerService (4 sub-steps!)

**a) Import:**
```kotlin
import es.wokis.commands.sound.SoundCommand
```

**b) Constructor:**
```kotlin
class CommandHandlerServiceImpl(
    private val playCommand: PlayCommand,
    private val soundCommand: SoundCommand,  // ADD HERE
    private val queueCommand: QueueCommand,
) : CommandHandlerService
```

**c) `onRegisterSimpleCommand()`:**
```kotlin
override fun onRegisterSimpleCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
    playCommand.onRegisterCommand(commandBuilder)
    soundCommand.onRegisterCommand(commandBuilder)  // ADD HERE
}
```

**d) `onExecute()`:**
```kotlin
override suspend fun onExecute(interaction, response) {
    when (val commandName = interaction.command.rootName) {
        CommandName.Play.commandName -> playCommand.onExecute(interaction, response)
        CommandName.Sound.commandName -> soundCommand.onExecute(interaction, response)  // ADD HERE
    }
}
```

**e) `onAutocomplete()` (if applicable):**
```kotlin
override suspend fun onAutocomplete(interaction: AutoCompleteInteraction) {
    when (val commandName = interaction.command.rootName) {
        CommandName.Sound.commandName -> soundCommand.onAutoComplete(interaction)
    }
}
```

### Step 7: Update Tests (`CommandHandlerServiceTest.kt`)

```kotlin
private val soundCommand: SoundCommand = mockk()  // ADD HERE

private val commandHandlerService = CommandHandlerServiceImpl(
    playCommand = playCommand,
    soundCommand = soundCommand,  // ADD HERE
)

@Test
fun `When onRegisterSimpleCommand is called Then register all commands`() {
    justRun { soundCommand.onRegisterCommand(any()) }  // ADD HERE

    verify(exactly = 1) {
        soundCommand.onRegisterCommand(commandBuilder)  // ADD HERE
    }
}
```

### Step 8: Create Command Tests

Create tests in `src/test/kotlin/commands/<command>/`:
```kotlin
class SoundCommandTest {
    // Test: Command registration
    // Test: Command execution
    // Test: Autocomplete
    // Test: Error handling
}
```

---

## Embeds

```kotlin
response.respond {
    embed {
        title = "Title"
        description = "Description"
        color = Color(0x01B05B)

        field {
            name = "Field Name"
            value = "Field Value"
            inline = true
        }

        footer {
            text = "Footer text"
        }
    }
}
```

### Paginated Embeds

```kotlin
createPaginatedEmbedMessage(
    locale = locale,
    localizationService = localizationService,
    title = "Title",
    currentPage = 1,
    currentPageContent = pageContent,  // List<String> (columns)
    columns = 3,                       // Max 3 inline columns
    pageCount = totalPages,
    previousButtonCustomId = "prev_btn",
    nextButtonCustomId = "next_btn"
)
```

### Limits
- Fields: max 25 per embed
- Field name: 256 chars
- Field value: 1024 chars
- Description: 4096 chars

---

## Button Interactions

```kotlin
override suspend fun onInteract(interaction: ComponentInteraction) {
    val customId = (interaction as? ButtonInteraction)?.component?.customId

    when (customId) {
        "prev_btn" -> handlePrevious()
        "next_btn" -> handleNext()
    }
}
```

---

## Voice Channels

```kotlin
suspend fun connectToVoiceChannel() {
    voiceChannel.connect {
        audioProvider {
            audioPlayer.provide(20, TimeUnit.MILLISECONDS)?.let {
                AudioFrame.fromData(it.data)
            }
        }
    }
}
```

---

## Caching Strategy

For static data like country codes (75x faster: 150ms → 2ms):

```kotlin
class RadioService {
    private var cachedCountryCodes: RadioCountryCodeDTO? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_EXPIRATION_MS = 3600000L  // 1 hour

    suspend fun getCountryCodes(): RemoteResponse<RadioCountryCodeDTO> {
        val currentTime = System.currentTimeMillis()

        if (cachedCountryCodes != null && (currentTime - cacheTimestamp) < CACHE_EXPIRATION_MS) {
            return RemoteResponse.Success(cachedCountryCodes!!)
        }

        return apiCall().also {
            cachedCountryCodes = it
            cacheTimestamp = currentTime
        }
    }
}
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Command doesn't appear | Missing `onRegisterSimpleCommand()` | Add command registration |
| "Unknown command" error | Missing `onExecute()` case | Add `when` branch |
| Autocomplete doesn't work | Missing `onAutoComplete()` case | Add `when` branch + check `autocomplete = true` |

---

## References
- `.github/instructions/discord-integration.instructions.md` — full context
