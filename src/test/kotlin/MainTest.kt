import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.DiscordPresence
import es.wokis.getPresence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainTest {
    @Test
    fun `Given debugMode on When getPresence() is called Then return debug mode presence`() {
        // Given
        val debugMode = true
        val expected = DiscordPresence(
            status = PresenceStatus.Online,
            afk = true,
            game = DiscordBotActivity(
                name = "~debug mode on",
                type = ActivityType.Game
            )
        )

        // When
        val actual = getPresence(debugMode)

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
        val actual = getPresence(debugMode)

        // Then
        assertEquals(actual, expected)
    }
}
