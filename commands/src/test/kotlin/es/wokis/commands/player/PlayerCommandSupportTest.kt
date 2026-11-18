package es.wokis.commands.player

import es.wokis.commands.neutral.CommandResponse
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.PlayerMessageKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerCommandSupportTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()

    @Test
    fun `Given a direct message When resolving the voice actor Then reply no guild and return null`() = runTest {
        val responder = strictResponder()

        val actor = responder.requireVoiceActor(commandContext(guildId = null), actions, localization.service)

        assertNull(actor)
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
    }

    @Test
    fun `Given a caller outside voice When resolving the voice actor Then reply no voice channel and return null`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        val actor = responder.requireVoiceActor(commandContext(), actions, localization.service)

        assertNull(actor)
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
    }

    @Test
    fun `Given a voice caller When resolving the voice actor Then return every actor field`() = runTest {
        val responder = strictResponder()
        val spanish = LocaleCode("es-ES")
        coEvery { actions.isVoiceAvailable(playerActor(guildLocale = spanish)) } returns true

        val actor = responder.requireVoiceActor(commandContext(guildLocale = spanish), actions, localization.service)

        assertEquals(GuildId(GUILD_ID.toULong()), actor?.guildId)
        assertEquals(UserId(USER_ID.toULong()), actor?.userId)
        assertEquals(ChannelId(CHANNEL_ID.toULong()), actor?.channelId)
        assertEquals(spanish, actor?.guildLocale)
        coVerify(exactly = 0) { responder.respond(any()) }
    }

    @Test
    fun `Given a formatted key When replying Then resolve it through the catalog format`() = runTest {
        val responder = strictResponder()

        responder.respondLocalizedFormat(
            localization.service,
            PlayerMessageKeys.NEXT_TRACK_MOVED,
            commandContext(),
            "Artist - Song",
        )

        coVerify(exactly = 1) {
            responder.respond(
                CommandResponse(localization.englishFormat(PlayerMessageKeys.NEXT_TRACK_MOVED, "Artist - Song")),
            )
        }
    }

    @Test
    fun `Given an unformatted key When replying Then resolve it without arguments`() = runTest {
        val responder = strictResponder()

        responder.respondLocalized(localization.service, PlayerMessageKeys.SKIP_COMMAND_RESPONSE, commandContext())

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SKIP_COMMAND_RESPONSE)))
        }
    }
}
