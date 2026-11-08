package es.wokis.commands.walkthrough

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughCommandTest {

    private val command = WalkthroughCommand()

    @Test
    fun `Given guild context When handle Then respond with neutral IDs and locale`() = runTest {
        // Given
        val responder = RecordingResponder()
        val context = context(
            guildId = GuildId(123uL),
            channelId = ChannelId(456uL),
            userId = UserId(789uL),
            locale = LocaleCode("es-ES"),
        )

        // When
        command.handle(context, responder)

        // Then
        assertEquals(
            "JDA walkthrough OK | guild=123 | channel=456 | user=789 | locale=es-ES",
            responder.responses.single().content,
        )
        assertTrue(responder.responses.single().ephemeral)
        assertTrue(responder.defers.isEmpty())
    }

    @Test
    fun `Given direct message context When handle Then report direct message and unknown locale`() = runTest {
        // Given
        val responder = RecordingResponder()
        val context = context(
            guildId = null,
            channelId = ChannelId(456uL),
            userId = UserId(789uL),
            locale = null,
        )

        // When
        command.handle(context, responder)

        // Then
        assertEquals(
            "JDA walkthrough OK | guild=direct-message | channel=456 | user=789 | locale=unknown",
            responder.responses.single().content,
        )
        assertTrue(responder.responses.single().ephemeral)
        assertTrue(responder.defers.isEmpty())
    }

    @Test
    fun `Given full unsigned guild id When handle Then print the exact neutral value`() = runTest {
        // Given
        val responder = RecordingResponder()
        val context = context(
            guildId = GuildId(ULong.MAX_VALUE),
            channelId = ChannelId(ULong.MAX_VALUE),
            userId = UserId(ULong.MAX_VALUE),
            locale = LocaleCode("de"),
        )

        // When
        command.handle(context, responder)

        // Then
        assertEquals(
            "JDA walkthrough OK | guild=18446744073709551615 | " +
                "channel=18446744073709551615 | user=18446744073709551615 | locale=de",
            responder.responses.single().content,
        )
    }

    @Test
    fun `Given walkthrough command When inspected Then expose the documented command name`() {
        // Then
        assertEquals("jda-walkthrough", command.name)
        assertEquals(WalkthroughCommand.COMMAND_NAME, command.name)
        assertTrue(WalkthroughCommand.COMMAND_DESCRIPTION.isNotBlank())
    }

    private fun context(
        guildId: GuildId?,
        channelId: ChannelId,
        userId: UserId,
        locale: LocaleCode?,
    ) = CommandContext(
        commandName = WalkthroughCommand.COMMAND_NAME,
        guildId = guildId,
        channelId = channelId,
        userId = userId,
        locale = locale,
    )

    private class RecordingResponder : CommandResponder {
        val defers = mutableListOf<Boolean>()
        val responses = mutableListOf<CommandResponse>()

        override suspend fun defer(ephemeral: Boolean) {
            defers += ephemeral
        }

        override suspend fun respond(response: CommandResponse) {
            responses += response
        }
    }
}
