package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.model.GuildId

/**
 * Thin JDA adapter for one neutral player command.
 *
 * It publishes the guild-scoped definition, defers the public reply and delegates every decision to
 * [delegate]; the reply itself is delivered by the delegate through the same responder.
 */
internal abstract class JdaNeutralPlayerCommand(
    private val delegate: CommandHandler,
    private val definition: CommandDefinition,
) : CommandHandler {

    override val name: String get() = delegate.name

    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, definition)
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.defer(ephemeral = false)
        delegate.handle(context, responder)
    }
}
