package es.wokis.commands.player

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/** Gateway-neutral `/play` and `/player` definitions, including their localized descriptions. */
object PlayerCommandDefinitions {
    const val PLAY_COMMAND_NAME = "play"
    const val PLAY_OPTION_INPUT = "input"
    const val PLAYER_COMMAND_NAME = "player"
    const val PLAYER_OPTION_ACTION = "action"

    fun play(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = PLAY_COMMAND_NAME,
        description = localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION),
        adminOnly = false,
        stringOption = StringOptionDefinition(
            name = PLAY_OPTION_INPUT,
            description = localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_INPUT_DESCRIPTION),
            required = true,
            descriptionLocalizations = localization.getLocalizations(
                PlayerMessageKeys.PLAY_COMMAND_INPUT_DESCRIPTION,
            ),
        ),
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION),
    )

    fun player(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = PLAYER_COMMAND_NAME,
        description = localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION),
        adminOnly = false,
        stringOption = StringOptionDefinition(
            name = PLAYER_OPTION_ACTION,
            description = localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION),
            required = true,
            descriptionLocalizations = localization.getLocalizations(
                PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION,
            ),
        ),
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION),
    )
}
