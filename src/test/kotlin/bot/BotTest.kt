package bot

import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.DiscordPresence
import es.wokis.bot.Bot
import es.wokis.services.commands.CommandHandlerService
import es.wokis.services.config.ConfigService
import es.wokis.services.processor.MessageProcessorService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BotTest {

    private val config: ConfigService = mockk()
    private val messageProcessor: MessageProcessorService = mockk()
    private val commandHandlerService: CommandHandlerService = mockk()

    private val bot = Bot(
        config = config,
        messageProcessor = messageProcessor,
        commandHandlerService = commandHandlerService
    )

    @Test
    fun `Given debugMode on When getPresence() is called Then return debug mode presence`() {
        // Given
        val debugMode = true
        val expected = DiscordPresence(
            status = PresenceStatus.Idle,
            afk = false,
            game = DiscordBotActivity(
                name = "~debug mode on",
                type = ActivityType.Game
            )
        )

        // When
        val actual = bot.getPresence(debugMode)

        // Then
        assertEquals(actual, expected)
    }

    @Test
    fun `Given debugMode off When getPresence() is called Then return regular mode presence`() {
        // Given
        val debugMode = false
        val expected = DiscordPresence(
            status = PresenceStatus.Online,
            afk = false,
            game = DiscordBotActivity(
                name = "~bip-bop",
                type = ActivityType.Game
            )
        )

        // When
        val actual = bot.getPresence(debugMode)

        // Then
        assertEquals(actual, expected)
    }
}
