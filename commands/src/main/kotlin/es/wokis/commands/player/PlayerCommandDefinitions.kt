package es.wokis.commands.player

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Gateway-neutral player command definitions, including their localized descriptions.
 *
 * Names, option names and required flags mirror Kord's registrations exactly, so both gateways
 * publish the same surface.
 */
object PlayerCommandDefinitions {
    const val PLAY_COMMAND_NAME = "play"
    const val PLAY_OPTION_INPUT = "input"
    const val PLAYER_COMMAND_NAME = "player"
    const val PLAYER_OPTION_ACTION = "action"

    const val SKIP_COMMAND_NAME = "skip"
    const val SHUFFLE_COMMAND_NAME = "shuffle"
    const val NEXT_COMMAND_NAME = "next"
    const val NEXT_OPTION_TRACK = "track"
    const val QUEUE_COMMAND_NAME = "queue"
    const val DISCONNECT_COMMAND_NAME = "disconnect"
    const val RECONNECT_COMMAND_NAME = "reconnect"

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
            // Beyond Kord's optionless `/player`: the optional action stays as an interim text
            // control alongside the player embed buttons.
            required = false,
            descriptionLocalizations = localization.getLocalizations(
                PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION,
            ),
        ),
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION),
    )

    /** Kord parity: `/skip` takes no options. */
    fun skip(localization: GuildLocalizationService): CommandDefinition =
        optionless(
            name = SKIP_COMMAND_NAME,
            descriptionKey = PlayerMessageKeys.SKIP_COMMAND_DESCRIPTION,
            localization = localization,
        )

    /** Kord parity: `/shuffle` takes no options. */
    fun shuffle(localization: GuildLocalizationService): CommandDefinition =
        optionless(
            name = SHUFFLE_COMMAND_NAME,
            descriptionKey = PlayerMessageKeys.SHUFFLE_COMMAND_DESCRIPTION,
            localization = localization,
        )

    /** Kord parity: `/queue` takes no options. */
    fun queue(localization: GuildLocalizationService): CommandDefinition =
        optionless(
            name = QUEUE_COMMAND_NAME,
            descriptionKey = PlayerMessageKeys.QUEUE_COMMAND_DESCRIPTION,
            localization = localization,
        )

    /** Kord parity: `/disconnect` takes no options. */
    fun disconnect(localization: GuildLocalizationService): CommandDefinition =
        optionless(
            name = DISCONNECT_COMMAND_NAME,
            descriptionKey = PlayerMessageKeys.DISCONNECT_COMMAND_DESCRIPTION,
            localization = localization,
        )

    /** Kord parity: `/reconnect` takes no options. */
    fun reconnect(localization: GuildLocalizationService): CommandDefinition =
        optionless(
            name = RECONNECT_COMMAND_NAME,
            descriptionKey = PlayerMessageKeys.RECONNECT_COMMAND_DESCRIPTION,
            localization = localization,
        )

    /** Kord parity: `/next` takes one required `track` string option. */
    fun next(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = NEXT_COMMAND_NAME,
        description = localization.getDefaultString(PlayerMessageKeys.NEXT_COMMAND_DESCRIPTION),
        adminOnly = false,
        stringOption = StringOptionDefinition(
            name = NEXT_OPTION_TRACK,
            description = localization.getDefaultString(PlayerMessageKeys.NEXT_COMMAND_INPUT_DESCRIPTION),
            required = true,
            descriptionLocalizations = localization.getLocalizations(
                PlayerMessageKeys.NEXT_COMMAND_INPUT_DESCRIPTION,
            ),
        ),
        descriptionLocalizations = localization.getLocalizations(PlayerMessageKeys.NEXT_COMMAND_DESCRIPTION),
    )

    private fun optionless(
        name: String,
        descriptionKey: String,
        localization: GuildLocalizationService,
    ): CommandDefinition = CommandDefinition(
        name = name,
        description = localization.getDefaultString(descriptionKey),
        adminOnly = false,
        descriptionLocalizations = localization.getLocalizations(descriptionKey),
    )
}
