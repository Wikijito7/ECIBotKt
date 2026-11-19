package es.wokis.commands.player

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerCommandDefinitionsTest {

    private val localization = PlayerLocalizationFixture()
    private val supportedLocales = setOf(LocaleCode("en-US"), LocaleCode("es-ES"))

    @Test
    fun `Given the skip definitions When built Then publish Kord's optionless public command`() {
        assertOptionless(
            PlayerCommandDefinitions.skip(localization.service),
            "skip",
            PlayerMessageKeys.SKIP_COMMAND_DESCRIPTION,
        )
        assertOptionless(
            PlayerCommandDefinitions.shuffle(localization.service),
            "shuffle",
            PlayerMessageKeys.SHUFFLE_COMMAND_DESCRIPTION,
        )
        assertOptionless(
            PlayerCommandDefinitions.queue(localization.service),
            "queue",
            PlayerMessageKeys.QUEUE_COMMAND_DESCRIPTION,
        )
        assertOptionless(
            PlayerCommandDefinitions.disconnect(localization.service),
            "disconnect",
            PlayerMessageKeys.DISCONNECT_COMMAND_DESCRIPTION,
        )
        assertOptionless(
            PlayerCommandDefinitions.reconnect(localization.service),
            "reconnect",
            PlayerMessageKeys.RECONNECT_COMMAND_DESCRIPTION,
        )
    }

    @Test
    fun `Given the next definition When built Then require the track option`() {
        val definition = PlayerCommandDefinitions.next(localization.service)

        assertEquals(PlayerCommandDefinitions.NEXT_COMMAND_NAME, definition.name)
        assertEquals(localization.english(PlayerMessageKeys.NEXT_COMMAND_DESCRIPTION), definition.description)
        assertFalse(definition.adminOnly)
        assertEquals(supportedLocales, definition.descriptionLocalizations.keys)
        val option = requireNotNull(definition.stringOption)
        assertEquals("track", option.name)
        assertEquals(PlayerCommandDefinitions.NEXT_OPTION_TRACK, option.name)
        assertEquals(localization.english(PlayerMessageKeys.NEXT_COMMAND_INPUT_DESCRIPTION), option.description)
        assertTrue(option.required, "Kord's /next requires the track option")
        assertFalse(option.autocomplete)
        assertEquals(supportedLocales, option.descriptionLocalizations.keys)
    }

    @Test
    fun `Given the play definition When built Then require the input option`() {
        val definition = PlayerCommandDefinitions.play(localization.service)

        assertEquals("play", definition.name)
        assertEquals(localization.english(PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION), definition.description)
        val option = requireNotNull(definition.stringOption)
        assertEquals("input", option.name)
        assertTrue(option.required)
        assertEquals(supportedLocales, option.descriptionLocalizations.keys)
    }

    @Test
    fun `Given the player definition When built Then keep the action option optional`() {
        val definition = PlayerCommandDefinitions.player(localization.service)

        assertEquals("player", definition.name)
        val option = requireNotNull(definition.stringOption)
        assertEquals("action", option.name)
        assertFalse(option.required)
    }

    @Test
    fun `Given every command name constant When compared Then it matches Kord's registration`() {
        assertEquals("play", PlayerCommandDefinitions.PLAY_COMMAND_NAME)
        assertEquals("input", PlayerCommandDefinitions.PLAY_OPTION_INPUT)
        assertEquals("player", PlayerCommandDefinitions.PLAYER_COMMAND_NAME)
        assertEquals("action", PlayerCommandDefinitions.PLAYER_OPTION_ACTION)
        assertEquals("skip", PlayerCommandDefinitions.SKIP_COMMAND_NAME)
        assertEquals("shuffle", PlayerCommandDefinitions.SHUFFLE_COMMAND_NAME)
        assertEquals("next", PlayerCommandDefinitions.NEXT_COMMAND_NAME)
        assertEquals("track", PlayerCommandDefinitions.NEXT_OPTION_TRACK)
        assertEquals("queue", PlayerCommandDefinitions.QUEUE_COMMAND_NAME)
        assertEquals("disconnect", PlayerCommandDefinitions.DISCONNECT_COMMAND_NAME)
        assertEquals("reconnect", PlayerCommandDefinitions.RECONNECT_COMMAND_NAME)
    }

    private fun assertOptionless(
        definition: CommandDefinition,
        expectedName: String,
        descriptionKey: String,
    ) {
        assertEquals(expectedName, definition.name)
        assertEquals(localization.english(descriptionKey), definition.description)
        assertFalse(definition.adminOnly)
        assertEquals(supportedLocales, definition.descriptionLocalizations.keys)
        assertNull(definition.stringOption, "/$expectedName takes no options")
    }
}
