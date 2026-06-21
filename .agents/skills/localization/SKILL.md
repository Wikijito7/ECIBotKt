---
name: localization
description: IMPORTANT: Load when adding user-facing strings or new languages. Defines the YAML-based localization system for ECIBotKt. Covers key generation, LocalizationService usage, and adding new locales. All user-facing strings MUST be localized.
---

## When to use me
- When adding user-facing strings (command descriptions, error messages, embed text)
- When adding new languages to the bot
- When debugging missing translation keys

## Not intended for
- Code quality checks → use `code-quality`
- Command registration (where to add keys) → use `discord-integration`
- Testing localization → use `testing`

---

## How It Works

### YAML Files

Located in `src/main/resources/lang/`:
```
lang/
├── lang.yml           # Default (English)
└── lang_es-ES.yml     # Spanish
```

### Key-Value Format

```yaml
# lang.yml
play_command_description: Play the sound with that name or the given url
now_playing: Now playing %s on %s
queue_embed_footer: Page %d of %d
```

### Auto-Generated Keys

```bash
./gradlew generateLangClass
```

Creates `LocalizationKeys.kt`:
```kotlin
object LocalizationKeys {
    const val PLAY_COMMAND_DESCRIPTION = "play_command_description"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE_EMBED_FOOTER = "queue_embed_footer"
}
```

---

## Adding New Strings

### Step 1: Add to both YAML files

```yaml
# lang.yml
my_new_key: This is the English text

# lang_es-ES.yml
my_new_key: Este es el texto en español
```

### Step 2: Generate keys

```bash
./gradlew generateLangClass
```

### Step 3: Use in code

```kotlin
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

// All language variants for Discord
descriptionLocalizations = localizationService.getLocalizations(
    LocalizationKeys.PLAY_COMMAND_DESCRIPTION
)
```

---

## LocalizationService Methods

| Method | Use For |
|--------|---------|
| `getString(key, locale)` | Simple text without formatting |
| `getStringFormat(key, locale, arguments)` | Text with placeholders (%s, %d) |
| `getLocalizations(key)` | All language variants for Discord |

### Default Locale

```kotlin
import es.wokis.utils.orDefaultLocale

val locale = interaction.guildLocale.orDefaultLocale()
```

---

## Formatting Placeholders

```yaml
my_key: Hello %s, you have %d messages
embed_footer: Page %d of %d
queue_description: Currently there are %d sounds in the queue of %s
```

```kotlin
localizationService.getStringFormat(
    key = LocalizationKeys.MY_KEY,
    locale = locale,
    arguments = arrayOf("User", 5)
)
// Result: "Hello User, you have 5 messages"
```

---

## Adding New Languages

1. Create new file: `lang_xx-XX.yml` (e.g., `lang_fr-FR.yml`)
2. Copy all keys from `lang.yml`
3. Translate values
4. Update `LocalizationService` to support new locale
5. Test with Discord client in that language

---

## Best Practices

1. **Always add to both language files** — Keep English and Spanish in sync
2. **Use descriptive keys** — `radio_play_found` better than `message_1`
3. **Group related keys** — Keep radio commands together
4. **Reuse keys when possible** — Don't duplicate similar strings
5. **Generate keys after editing YAML** — Run `generateLangClass`
6. **Test both languages** — Verify text displays correctly

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Exception: Key 'my_key' not found` | Run `./gradlew generateLangClass` |
| Missing translation | Add key to both `lang.yml` and `lang_es-ES.yml` |
| LSP errors in YAML | Expected due to special characters — no runtime impact |

---

## References
- `.github/instructions/localization.instructions.md` — full context
- Generated file: `src/main/kotlin/localization/LocalizationKeys.kt`
