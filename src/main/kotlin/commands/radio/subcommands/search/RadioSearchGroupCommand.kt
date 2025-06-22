package es.wokis.commands.radio.subcommands.search

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.group
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.SubCommand

class RadioSearchGroupCommand(
    private val radioSearchNameCommand: RadioSearchNameCommand,
    private val radioSearchCountryCodeCommand: RadioSearchCountryCodeCommand
) : SubCommand, Component {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            group(CommandName.Radio.Search.commandName, "aasdsa") {
                subCommand(CommandName.Radio.Search.Name.commandName, "asdasd") {}
                subCommand(CommandName.Radio.Search.CountryCode.commandName, "sese") {}
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        (interaction.command as? GroupCommand)?.name?.let { commandName ->
            when (commandName) {
                CommandName.Radio.Search.Name.commandName -> radioSearchNameCommand.onExecute(interaction, response)
                CommandName.Radio.Search.CountryCode.commandName -> radioSearchCountryCodeCommand.onExecute(interaction, response)
            }
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        TODO("Not yet implemented")
    }
}
