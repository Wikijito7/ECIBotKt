package es.wokis.commands.neutral

import es.wokis.message.ComponentId

/** Gateway-neutral handler for one component id. */
interface ComponentHandler {

    val id: ComponentId

    suspend fun handle(context: ComponentContext, responder: ComponentResponder)
}
