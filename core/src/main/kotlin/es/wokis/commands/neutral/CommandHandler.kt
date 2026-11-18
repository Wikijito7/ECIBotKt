package es.wokis.commands.neutral

/**
 * Gateway-neutral command handler.
 *
 * Implementations must only use the [CommandContext] and [CommandResponder] boundary; gateway
 * types belong in adapter modules.
 */
interface CommandHandler {

    val name: String

    suspend fun handle(context: CommandContext, responder: CommandResponder)
}
