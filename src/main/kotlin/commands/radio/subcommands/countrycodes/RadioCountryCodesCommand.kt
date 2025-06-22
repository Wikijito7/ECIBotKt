package es.wokis.commands.radio.subcommands.countrycodes

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import es.wokis.commands.SubCommand

class RadioCountryCodesCommand : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        TODO("Not yet implemented")
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        TODO("Not yet implemented")
    }
}
