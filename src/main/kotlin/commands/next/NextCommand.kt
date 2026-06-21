package es.wokis.commands.next

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getDisplayTrackName
import es.wokis.utils.isValidUrl
import es.wokis.utils.takeIfNotEmpty
import es.wokis.utils.transformUrl

private const val ARGUMENT_NAME = "track"

class NextCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Next.commandName,
                description = localizationService.getLocalizations(
                    LocalizationKeys.NEXT_COMMAND_DESCRIPTION
                ).values.first()
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.NEXT_COMMAND_DESCRIPTION)
                string(
                    name = ARGUMENT_NAME,
                    description = localizationService.getLocalizations(
                        LocalizationKeys.NEXT_COMMAND_INPUT_DESCRIPTION
                    ).values.first()
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.NEXT_COMMAND_INPUT_DESCRIPTION)
                    required = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        val input: String = interaction.command.strings[ARGUMENT_NAME]?.takeIfNotEmpty()
            ?: response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(ARGUMENT_NAME)
                )
            }.let { return }

        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)

        if (input.isValidUrl()) {
            // URL case: transform monochrome URLs and load as next
            val transformedUrl = input.transformUrl()
            response.respond {
                content = localizationService.getString(LocalizationKeys.SEARCHING_SONG, guildId, discordLocale)
            }
            guildLavaPlayerService.loadAndPlay(transformedUrl, addToFront = true)
        } else {
            // Search case: find track in queue and move to next position
            if (guildLavaPlayerService.isQueueEmpty()) {
                response.respond {
                    content = localizationService.getString(
                        LocalizationKeys.NEXT_EMPTY_QUEUE,
                        guildId,
                        discordLocale
                    )
                }
                return
            }

            val movedTrack = guildLavaPlayerService.moveTrackToNext(input)

            if (movedTrack != null) {
                response.respond {
                    content = localizationService.getStringFormat(
                        key = LocalizationKeys.NEXT_TRACK_MOVED,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(movedTrack.getDisplayTrackName())
                    )
                }
            } else {
                // Track not found in queue, warn user and try lavaplayer anyway
                // This handles special commands like dzrec:1090538082
                response.respond {
                    content = localizationService.getStringFormat(
                        key = LocalizationKeys.NEXT_TRACK_NOT_IN_QUEUE_TRYING_SEARCH,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(input)
                    )
                }
                guildLavaPlayerService.loadAndPlay(input, addToFront = true)
            }
        }
    }
}
