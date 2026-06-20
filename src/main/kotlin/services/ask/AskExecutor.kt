package es.wokis.services.ask

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService

class AskExecutor(
    private val askService: AskService,
    private val localizationService: LocalizationService,
    private val configService: ConfigService
) {
    suspend fun execute(
        prompt: String,
        guildId: Snowflake?,
        discordLocale: Locale?,
        response: DeferredPublicMessageInteractionResponseBehavior,
        guildLavaPlayerService: GuildLavaPlayerService
    ) {
        if (!configService.config.ask.enabled) {
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.ASK_NOT_ENABLED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val initialResponse: PublicMessageInteractionResponse = response.respond {
            content = localizationService.getString(
                key = LocalizationKeys.ASK_THINKING,
                guildId = guildId,
                discordLocale = discordLocale
            )
        }

        try {
            val answer = askService.askAndPlayTTS(prompt, guildLavaPlayerService)
            initialResponse.edit {
                content = answer
            }
        } catch (e: Exception) {
            initialResponse.edit {
                content = localizationService.getString(
                    key = LocalizationKeys.ERROR_API_UNEXPECTED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
        }
    }
}
