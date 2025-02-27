package es.wokis.commands.tts

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.tts.TTSService
import es.wokis.utils.getArgument
import es.wokis.utils.orDefaultLocale

private const val TTS_ARGUMENT_NAME = "message"

class TTSCommand(
    private val ttsService: TTSService,
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService
) : Command {
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandsEnum.TTS.commandName,
                description = "tts"
            ) {
                string(
                    name = TTS_ARGUMENT_NAME,
                    description = "mensaje"
                ) {
                    required = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val locale = interaction.guildLocale.orDefaultLocale()
            val message: String = interaction.getArgument(TTS_ARGUMENT_NAME)
                ?: response.respond {
                    content = localizationService.getStringFormat(
                        key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                        locale = locale,
                        arguments = arrayOf(TTS_ARGUMENT_NAME)
                    )
                }.let { return }
            val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
            response.respond { content = "Generando mensaje tts" }
            ttsService.loadAndPlayMessage(guildLavaPlayerService, message)
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }
}
