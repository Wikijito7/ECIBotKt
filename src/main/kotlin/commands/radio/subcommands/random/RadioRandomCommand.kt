package es.wokis.commands.radio.subcommands.random

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.data.radio.RadioDTO
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.radio.RadioService
import es.wokis.utils.orDefaultLocale

class RadioRandomCommand(
    private val radioService: RadioService,
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.Random.commandName, localizationService.getString(LocalizationKeys.RADIO_RANDOM_COMMAND_DESCRIPTION)) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.RADIO_RANDOM_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val locale = interaction.guildLocale.orDefaultLocale()
        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction)

        response.respond {
            content = localizationService.getString(
                key = LocalizationKeys.RADIO_RANDOM_SEARCHING,
                locale = locale
            )
        }

        when (val result = radioService.getRandomRadio()) {
            is RemoteResponse.Success -> {
                result.data?.let { radio ->
                    guildLavaPlayerService.playRadio(
                        radioName = radio.radioName,
                        radioUrl = radio.url,
                        customFavicon = radio.thumbnailUrl
                    )
                } ?: response.respond {
                    content = localizationService.getString(
                        key = LocalizationKeys.RADIO_RANDOM_ERROR,
                        locale = locale
                    )
                }
            }

            is RemoteResponse.Error -> {
                response.respond {
                    content = localizationService.getString(
                        key = LocalizationKeys.RADIO_RANDOM_ERROR,
                        locale = locale
                    )
                }
            }
        }
    }
}
