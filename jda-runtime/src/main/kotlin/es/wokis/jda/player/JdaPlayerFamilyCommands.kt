package es.wokis.jda.player

import es.wokis.commands.disconnect.DisconnectCommandHandler
import es.wokis.commands.next.NextCommandHandler
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.queue.QueueCommandHandler
import es.wokis.commands.reconnect.ReconnectCommandHandler
import es.wokis.commands.shuffle.ShuffleCommandHandler
import es.wokis.commands.skip.SkipCommandHandler
import es.wokis.localization.GuildLocalizationService

/** `/skip`; Kord parity: no options and a public confirmation. */
internal class JdaSkipCommand(
    handler: SkipCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, PlayerCommandDefinitions.skip(localizationService))

/** `/shuffle`; Kord parity: no options and a public confirmation. */
internal class JdaShuffleCommand(
    handler: ShuffleCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, PlayerCommandDefinitions.shuffle(localizationService))

/** `/next`; Kord parity: one required `track` string option. */
internal class JdaNextCommand(
    handler: NextCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, PlayerCommandDefinitions.next(localizationService))

/** `/queue`; Kord parity: no options and a public paginated embed. */
internal class JdaQueueCommand(
    handler: QueueCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, PlayerCommandDefinitions.queue(localizationService))

/** `/disconnect`; Kord parity: no options and a public confirmation. */
internal class JdaDisconnectCommand(
    handler: DisconnectCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, PlayerCommandDefinitions.disconnect(localizationService))

/** `/reconnect`; Kord parity: no options and a public confirmation. */
internal class JdaReconnectCommand(
    handler: ReconnectCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, PlayerCommandDefinitions.reconnect(localizationService))
