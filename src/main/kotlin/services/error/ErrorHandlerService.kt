package es.wokis.services.error

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.Interaction
import es.wokis.exceptions.BotException
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_DISCORD_MESSAGE_LENGTH = 2000
private const val STACK_TRACE_MAX_LENGTH = 1900
private const val ERROR_HEADER_LENGTH = 100
private const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"
private const val TRUNCATED_SUFFIX = "\n… (truncated)"

/**
 * Enum representing different types of interactions that can generate errors.
 */
enum class InteractionType(val displayName: String) {
    COMMAND("Command"),
    BUTTON("Button"),
    AUTOCOMPLETE("Autocomplete")
}

/**
 * Service responsible for centralized error handling across the bot.
 *
 * This service:
 * - Logs all errors to console
 * - Sends detailed error reports to Discord in debug mode
 * - Responds to users with appropriate error messages (ephemeral)
 * - Handles different exception types with specific behaviors
 */
class ErrorHandlerService(
    private val configService: ConfigService,
    private val localizationService: LocalizationService
) {

    /**
     * Handles exceptions from command execution.
     *
     * @param exception The exception that was thrown
     * @param interaction The command interaction context
     * @param response The deferred response to reply to
     * @param commandName Optional command name for context
     */
    suspend fun handleCommandError(
        exception: Throwable,
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior,
        commandName: String? = null
    ) {
        handleError(exception, interaction, commandName, InteractionType.COMMAND) {
            respondToUser(exception, interaction, response)
        }
    }

    /**
     * Handles exceptions from button interactions.
     *
     * @param exception The exception that was thrown
     * @param interaction The button interaction context
     * @param commandName Optional command name for context
     */
    suspend fun handleInteractionError(
        exception: Throwable,
        interaction: ButtonInteraction,
        commandName: String? = null
    ) {
        handleError(exception, interaction, commandName, InteractionType.BUTTON) {
            // Button interactions can't be ephemeral after defer, so we just log
            // The user will see a generic error from Discord
        }
    }

    /**
     * Handles exceptions from autocomplete interactions.
     * Autocomplete errors are silently logged and return empty suggestions.
     *
     * @param exception The exception that was thrown
     * @param interaction The autocomplete interaction context
     * @param commandName Optional command name for context
     */
    suspend fun handleAutocompleteError(
        exception: Throwable,
        interaction: AutoCompleteInteraction,
        commandName: String? = null
    ) {
        handleError(exception, interaction, commandName, InteractionType.AUTOCOMPLETE) {
            // Return empty suggestions on error
            interaction.suggest(emptyList())
        }
    }

    /**
     * Common error handling logic for all interaction types.
     */
    private suspend fun handleError(
        exception: Throwable,
        interaction: Interaction,
        commandName: String?,
        interactionType: InteractionType,
        onUserResponse: suspend () -> Unit
    ) {
        // Always log to console
        logError(exception, interaction, commandName, interactionType)

        // Send to Discord if in debug mode
        if (configService.config.debug) {
            sendErrorToDiscord(exception, interaction, commandName, interactionType)
        }

        // Execute user response callback
        onUserResponse()
    }

    /**
     * Logs error details to console with full context.
     */
    private fun logError(
        exception: Throwable,
        interaction: Interaction,
        commandName: String?,
        interactionType: InteractionType
    ) {
        val guildInfo = if (interaction.data.guildId.value != null) "Guild(${interaction.data.guildId.value})" else "DM"
        val context = "${interactionType.displayName} Error: ${commandName ?: "Unknown"} | User: ${interaction.user.username} (${interaction.user.id}) | Guild: $guildInfo"
        Log.error(context, exception)
    }

    /**
     * Sends detailed error information to the Discord channel.
     * Only called when debug mode is enabled.
     */
    private suspend fun sendErrorToDiscord(
        exception: Throwable,
        interaction: Interaction,
        commandName: String?,
        interactionType: InteractionType
    ) {
        try {
            val channel = interaction.channel
            val errorMessage = buildErrorDiscordMessage(exception, interaction, commandName, interactionType)

            // Truncate if necessary
            val truncatedMessage = if (errorMessage.length > MAX_DISCORD_MESSAGE_LENGTH) {
                errorMessage.take(MAX_DISCORD_MESSAGE_LENGTH - 3) + "…"
            } else {
                errorMessage
            }

            channel.createMessage(truncatedMessage)
        } catch (e: Exception) {
            Log.error("Failed to send error to Discord", e)
        }
    }

    /**
     * Builds the error message for Discord with full context and stack trace.
     */
    private fun buildErrorDiscordMessage(
        exception: Throwable,
        interaction: Interaction,
        commandName: String?,
        interactionType: InteractionType
    ): String {
        val timestamp = DateTimeFormatter
            .ofPattern(TIMESTAMP_PATTERN)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())

        val stackTrace = exception.stackTraceToString()
        val truncatedStackTrace = if (stackTrace.length > STACK_TRACE_MAX_LENGTH) {
            stackTrace.take(STACK_TRACE_MAX_LENGTH) + TRUNCATED_SUFFIX
        } else {
            stackTrace
        }

        val guildInfo = if (interaction.data.guildId.value != null) "Guild(${interaction.data.guildId.value})" else "DM"

        return buildString {
            appendLine("**BOT ERROR** 💀")
            appendLine()
            appendLine("**${interactionType.displayName}:** ${commandName ?: "Unknown"}")

            // Add Custom ID for button interactions
            if (interaction is ButtonInteraction) {
                appendLine("**Custom ID:** ${interaction.component.customId ?: "N/A"}")
            }

            appendLine("**User:** ${interaction.user.username} (`${interaction.user.id}`)")
            appendLine("**Guild:** $guildInfo")
            appendLine("**Time:** $timestamp")
            appendLine()
            appendLine("**Exception:** `${exception.javaClass.simpleName}`")
            appendLine("**Message:** ${exception.message ?: "No message"}")
            appendLine()
            appendLine("**Stack Trace:**")
            appendLine("```java")
            appendLine(truncatedStackTrace)
            appendLine("```")
        }
    }

    /**
     * Responds to the user with an appropriate error message.
     * UserException -> localized user-friendly message
     * APIException -> localized API error message
     * SystemException -> generic error message (or debug info if in debug mode)
     * Other exceptions -> generic error message
     */
    private suspend fun respondToUser(
        exception: Throwable,
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale

        val message = when (exception) {
            is BotException.UserException -> {
                // User errors have localization keys
                localizationService.getStringFormat(
                    key = exception.localizationKey,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = exception.args
                )
            }

            is BotException.APIException -> {
                // API errors - use generic API error message
                localizationService.getStringFormat(
                    key = LocalizationKeys.ERROR_API_UNEXPECTED,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(exception.stackTraceToString())
                )
            }

            is BotException.SystemException -> {
                // System errors - show generic message, debug info only in debug mode
                if (configService.config.debug) {
                    localizationService.getStringFormat(
                        key = LocalizationKeys.ERROR_UNEXPECTED_WITH_DEBUG,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(exception.originalException?.message ?: exception.message ?: "Unknown")
                    )
                } else {
                    localizationService.getString(
                        key = LocalizationKeys.ERROR_UNEXPECTED,
                        guildId = guildId,
                        discordLocale = discordLocale
                    )
                }
            }

            else -> {
                // Unknown exceptions - generic message
                if (configService.config.debug) {
                    localizationService.getStringFormat(
                        key = LocalizationKeys.ERROR_UNEXPECTED_WITH_DEBUG,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(exception.message ?: "Unknown")
                    )
                } else {
                    localizationService.getString(
                        key = LocalizationKeys.ERROR_UNEXPECTED,
                        guildId = guildId,
                        discordLocale = discordLocale
                    )
                }
            }
        }

        response.respond {
            content = message
        }
    }
}
