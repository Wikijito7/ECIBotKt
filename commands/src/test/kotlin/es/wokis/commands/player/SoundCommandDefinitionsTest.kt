package es.wokis.commands.player

import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundCommandDefinitionsTest {

    private val localization = PlayerLocalizationFixture()
    private val supportedLocales = setOf(LocaleCode("en-US"), LocaleCode("es-ES"))

    @Test
    fun `Given the sound definition When built Then publish the public autocompleted name option`() {
        val definition = SoundCommandDefinitions.sound(localization.service)

        assertEquals("sound", definition.name)
        assertEquals(localization.english(PlayerMessageKeys.SOUND_COMMAND_DESCRIPTION), definition.description)
        assertFalse(definition.adminOnly)
        assertEquals(supportedLocales, definition.descriptionLocalizations.keys)
        val option = requireNotNull(definition.stringOption)
        assertEquals("name", option.name)
        assertEquals(PlayerCommandDefinitions.SOUND_OPTION_NAME, option.name)
        assertEquals(
            localization.english(PlayerMessageKeys.SOUND_COMMAND_INPUT_DESCRIPTION),
            option.description,
        )
        assertTrue(option.required, "Kord's /sound requires the name option")
        assertTrue(option.autocomplete, "Kord's /sound drives the name option through autocomplete")
        assertEquals(supportedLocales, option.descriptionLocalizations.keys)
    }

    @Test
    fun `Given the sounds definition When built Then publish the optionless public list command`() {
        val definition = SoundCommandDefinitions.sounds(localization.service)

        assertEquals("sounds", definition.name)
        assertEquals(localization.english(PlayerMessageKeys.SOUNDS_DESCRIPTION), definition.description)
        assertFalse(definition.adminOnly)
        assertNull(definition.stringOption)
        assertEquals(supportedLocales, definition.descriptionLocalizations.keys)
    }

    @Test
    fun `Given the tts definition When built Then publish the public required message option without autocomplete`() {
        val definition = SoundCommandDefinitions.tts(localization.service)

        assertEquals("tts", definition.name)
        assertEquals(localization.english(PlayerMessageKeys.TTS_COMMAND_DESCRIPTION), definition.description)
        assertFalse(definition.adminOnly)
        val option = requireNotNull(definition.stringOption)
        assertEquals("message", option.name)
        assertEquals(PlayerCommandDefinitions.TTS_OPTION_MESSAGE, option.name)
        assertEquals(localization.english(PlayerMessageKeys.TTS_COMMAND_INPUT_DESCRIPTION), option.description)
        assertTrue(option.required)
        assertFalse(option.autocomplete, "Kord's /tts does not autocomplete free text")
        assertEquals(supportedLocales, option.descriptionLocalizations.keys)
    }
}
