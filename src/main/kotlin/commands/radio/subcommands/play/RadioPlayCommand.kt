package es.wokis.commands.radio.subcommands.play

import dev.kord.common.entity.Choice
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand

class RadioPlayCommand(
    // private val radioService: RadioService
) : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.Play.commandName, "asdasd") {
//                options = mutableListOf(
//                    StringChoiceBuilder(
//                        CommandName.Radio.Play.commandName,
//                        "asdasd"
//                    ).apply {
//                        choice("Manolete", "asd")
//                    }
//                )
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        response.respond {
            content = "play blablablas"
        }
    }
}
