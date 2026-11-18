package es.wokis.jda.player

import es.wokis.commands.player.SoundCommandDefinitions
import es.wokis.commands.sound.SoundCommandHandler
import es.wokis.commands.sounds.SoundsCommandHandler
import es.wokis.commands.tts.TtsCommandHandler
import es.wokis.localization.GuildLocalizationService
import es.wokis.message.ComponentId

/** `/sound`; Kord parity: one required, autocompleted `name` string option. */
internal class JdaSoundCommand(
    handler: SoundCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, SoundCommandDefinitions.sound(localizationService))

/** `/sounds`; Kord parity: no options and a public paginated embed. */
internal class JdaSoundsCommand(
    handler: SoundsCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, SoundCommandDefinitions.sounds(localizationService))

/** `/tts`; Kord parity: one required `message` string option. */
internal class JdaTtsCommand(
    handler: TtsCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, SoundCommandDefinitions.tts(localizationService))

/** Neutral `/sounds` pagination ids wired to the component router. */
internal val SOUNDS_PAGINATION_IDS: List<ComponentId> = listOf(
    ComponentId.SOUNDS_PREVIOUS,
    ComponentId.SOUNDS_NEXT,
)
