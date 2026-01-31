# Localization Instructions

## Overview

ECIBotKt supports multiple languages (English and Spanish) using a YAML-based localization system with auto-generated Kotlin keys.

## How It Works

### 1. YAML Files

Language files located in `src/main/resources/lang/`:

```
lang/
├── lang.yml           # Default (English)
└── lang_es-ES.yml     # Spanish
```

### 2. Key-Value Format

```yaml
# lang.yml
play_command_description: Play the sound with that name or the given url
play_command_input_description: Sounds to play separated by a space
now_playing: 🎶 Now playing %s on %s
queue_embed_title: Sound queue
queue_embed_footer: Page %d of %d
```

### 3. Auto-Generated Keys

Run Gradle task to generate Kotlin constants:

```bash
./gradlew generateLangClass
```

This creates `LocalizationKeys.kt`:

```kotlin
object LocalizationKeys {
    const val PLAY_COMMAND_DESCRIPTION = "play_command_description"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE_EMBED_TITLE = "queue_embed_title"
    const val QUEUE_EMBED_FOOTER = "queue_embed_footer"
}
```

## Adding New Strings

### Step 1: Add to YAML

Add to both `lang.yml` and `lang_es-ES.yml`:

```yaml
# lang.yml
my_new_key: This is the English text

# lang_es-ES.yml
my_new_key: Este es el texto en español
```

### Step 2: Generate Keys

```bash
./gradlew generateLangClass
```

### Step 3: Use in Code

```kotlin
import es.wokis.localization.LocalizationKeys

// Simple string
val text = localizationService.getString(
    key = LocalizationKeys.MY_NEW_KEY,
    locale = interaction.guildLocale.orDefaultLocale()
)

// Formatted string with arguments
val formatted = localizationService.getStringFormat(
    key = LocalizationKeys.QUEUE_EMBED_FOOTER,
    locale = locale,
    arguments = arrayOf(currentPage, totalPages)
)
```

### Step 4: Localize Command Descriptions

```kotlin
builder.subCommand(
    CommandName.Play.commandName,
    localizationService.getString(LocalizationKeys.PLAY_COMMAND_DESCRIPTION)
) {
    descriptionLocalizations = localizationService.getLocalizations(
        LocalizationKeys.PLAY_COMMAND_DESCRIPTION
    )
}
```

## LocalizationService

### Methods

| Method | Use For |
|--------|---------|
| `getString(key, locale)` | Simple text without formatting |
| `getStringFormat(key, locale, arguments)` | Text with placeholders (%s, %d) |
| `getLocalizations(key)` | All language variants for Discord |

### Default Locale

Use extension function for fallback:

```kotlin
import es.wokis.utils.orDefaultLocale

val locale = interaction.guildLocale.orDefaultLocale()
```

## Formatting Placeholders

### String Format
```yaml
my_key: Hello %s, you have %d messages
```

```kotlin
localizationService.getStringFormat(
    key = LocalizationKeys.MY_KEY,
    locale = locale,
    arguments = arrayOf("User", 5)
)
// Result: "Hello User, you have 5 messages"
```

### Multiple Placeholders
```yaml
embed_footer: Page %d of %d
queue_description: Currently there are %d sounds in the queue of %s
```

## Best Practices

1. **Always add to both language files** - Keep English and Spanish in sync
2. **Use descriptive keys** - `radio_play_found` better than `message_1`
3. **Group related keys** - Keep radio commands together
4. **Reuse keys when possible** - Don't duplicate similar strings
5. **Generate keys after editing YAML** - Run `generateLangClass`
6. **Test both languages** - Verify text displays correctly

## Common Keys

### Command Descriptions
```yaml
play_command_description: Play the sound with that name or the given url
queue_command_description: Shows the current queue
radio_command_description: Manage radio stations
```

### Error Messages
```yaml
error_no_voice_channel: You need to be connected to a voice channel
error_no_guild: You need to be in a server
```

### Success Messages
```yaml
added_track_to_queue: Added %s to the queue
now_playing: 🎶 Now playing %s on %s
```

### UI Labels
```yaml
queue_previous_button_label: Previous
queue_next_button_label: Next
player_pause: Pause
player_resume: Resume
```

## YAML Syntax Notes

- Keys use underscores: `my_key_name`
- Values can use special characters
- Multiline strings use `|` or `>` (not recommended for localization)
- Escape special Discord markdown if needed

## Troubleshooting

### Key not found
```
Exception: Key 'my_key' not found
```
**Fix**: Run `./gradlew generateLangClass` to regenerate keys

### Missing translation
**Fix**: Add key to both `lang.yml` and `lang_es-ES.yml`

### LSP errors in YAML
These are expected due to special characters in localization strings - they don't affect runtime.

## Adding New Languages

1. Create new file: `lang_xx-XX.yml` (e.g., `lang_fr-FR.yml`)
2. Copy all keys from `lang.yml`
3. Translate values
4. Update `LocalizationService` to support new locale
5. Test with Discord client in that language

## References

- Gradle task: `generateLangClass`
- Generated file: `src/main/kotlin/localization/LocalizationKeys.kt`
- Service: `src/main/kotlin/services/localization/LocalizationService.kt`
