package es.wokis.commands.player

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Sound family definitions, split from [PlayerCommandDefinitions] to stay inside Detekt's object
 * function threshold. Names, option names and required flags mirror Kord exactly.
 */
object SoundCommandDefinitions {

    /** Kord parity: `/sound` takes one required, autocompleted `name` string option. */
    fun sound(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = PlayerCommandDefinitions.SOUND_COMMAND_NAME,
        description = localization.getDefaultString(PlayerMessageKeys.SOUND_COMMAND_DESCRIPTION),
        adminOnly = false,
        stringOption = StringOptionDefinition(
            name = PlayerCommandDefinitions.SOUND_OPTION_NAME,
            description = localization.getDefaultString(PlayerMessageKeys.SOUND_COMMAND_INPUT_DESCRIPTION),
            required = true,
            autocomplete = true,
            descriptionLocalizations = localization.getLocalizations(
                PlayerMessageKeys.SOUND_COMMAND_INPUT_DESCRIPTION,
            ),
        ),
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.SOUND_COMMAND_DESCRIPTION),
    )

    /** Kord parity: `/sounds` takes no options. */
    fun sounds(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = PlayerCommandDefinitions.SOUNDS_COMMAND_NAME,
        description = localization.getDefaultString(PlayerMessageKeys.SOUNDS_DESCRIPTION),
        adminOnly = false,
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.SOUNDS_DESCRIPTION),
    )

    /** Kord parity: `/tts` takes one required `message` string option. */
    fun tts(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = PlayerCommandDefinitions.TTS_COMMAND_NAME,
        description = localization.getDefaultString(PlayerMessageKeys.TTS_COMMAND_DESCRIPTION),
        adminOnly = false,
        stringOption = StringOptionDefinition(
            name = PlayerCommandDefinitions.TTS_OPTION_MESSAGE,
            description = localization.getDefaultString(PlayerMessageKeys.TTS_COMMAND_INPUT_DESCRIPTION),
            required = true,
            descriptionLocalizations = localization.getLocalizations(
                PlayerMessageKeys.TTS_COMMAND_INPUT_DESCRIPTION,
            ),
        ),
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.TTS_COMMAND_DESCRIPTION),
    )
}
