package es.wokis.commands.radio.subcommands.play

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.Autocomplete
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.radio.RadioService
import es.wokis.utils.orDefaultLocale
import es.wokis.utils.takeAtMost
import es.wokis.utils.takeIfNotEmpty

private const val RADIO_INPUT_NAME = "radio"

class RadioPlayCommand(
    private val radioService: RadioService,
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : SubCommand, Autocomplete {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.Play.commandName, "asdasd") {
                string(RADIO_INPUT_NAME, "Radio name") {
                    required = true
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val radioName = interaction.command.strings[RADIO_INPUT_NAME]
        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction)
        radioName?.let {
            val locale = interaction.guildLocale.orDefaultLocale()
            val originalResponse = response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.SEARCHING_SONG,
                    locale = locale
                )
            }
            radioService.findRadio(radioName).let { radio ->
                originalResponse.edit {
                    content = radio?.let { "Radio found, tuning in…" } ?: "Radio not found, try again."
                }
                radio?.let {
                    guildLavaPlayerService.playRadio(
                        radioName = radio.radioName,
                        radioUrl = radio.url,
                        customFavicon = radio.thumbnailUrl
                    )
                }
            }
        } ?: response.respond {
            content = "RadioName is required"
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings[RADIO_INPUT_NAME].orEmpty()
        input.takeIfNotEmpty()?.let {
            val choices = (radioService.searchRadio(input) as? RemoteResponse.Success)?.data?.map {
                Choice.StringChoice(it.radioName.takeAtMost(100), Optional.Missing(), it.radioName.takeAtMost(100))
            }.orEmpty()
            interaction.suggest(choices)
        } ?: interaction.suggest(emptyList())
    }
}
