package es.wokis.commands.radio.subcommands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.SubCommand

class RadioPlayCommand : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand("play", "play") {

            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        TODO("Not yet implemented")
    }
}
