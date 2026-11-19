package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Backward-compatibility guard for the group-command model: the existing single-option commands keep
 * their exact shape, and a plain invocation carries no subcommand segments.
 */
class CommandDefinitionDefaultsTest {

    @Test
    fun `Given a plain single option definition When built Then publish no subcommands or groups`() {
        val definition = CommandDefinition(
            name = "next",
            description = "Skip to a track",
            stringOption = StringOptionDefinition(name = "track", description = "Track", required = true),
        )

        assertTrue(definition.subcommands.isEmpty(), "a plain command must not declare subcommands")
        assertTrue(definition.subcommandGroups.isEmpty(), "a plain command must not declare subcommand groups")
        assertEquals(
            CommandDefinition(
                name = "next",
                description = "Skip to a track",
                stringOption = StringOptionDefinition(name = "track", description = "Track", required = true),
                subcommands = emptyList(),
                subcommandGroups = emptyList(),
            ),
            definition,
        )
    }

    @Test
    fun `Given a group definition When built Then expose its leaves and its one nesting level`() {
        val play = SubcommandDefinition(name = "play", description = "Play")
        val search = SubcommandGroupDefinition(
            name = "search",
            description = "Search",
            subcommands = listOf(SubcommandDefinition(name = "name", description = "By name")),
        )

        val definition = CommandDefinition(
            name = "radio",
            description = "Radio",
            subcommands = listOf(play),
            subcommandGroups = listOf(search),
        )

        assertEquals(listOf(play), definition.subcommands)
        assertEquals(listOf(search), definition.subcommandGroups)
        assertNull(definition.stringOption, "a group command must not also publish a top-level option")
        assertEquals(mapOf<LocaleCode, String>(), definition.descriptionLocalizations)
    }

    @Test
    fun `Given both a top-level option and subcommands When built Then reject the definition`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            CommandDefinition(
                name = "radio",
                description = "Radio",
                stringOption = StringOptionDefinition(name = "radio", description = "Station", required = true),
                subcommands = listOf(SubcommandDefinition(name = "play", description = "Play")),
            )
        }

        assertTrue(
            failure.message.orEmpty().contains("must not declare a top-level string option"),
            "the failure did not explain the structural rule: ${failure.message}",
        )
    }

    @Test
    fun `Given both a top-level option and subcommand groups When built Then reject the definition`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            CommandDefinition(
                name = "radio",
                description = "Radio",
                stringOption = StringOptionDefinition(name = "radio", description = "Station", required = true),
                subcommandGroups = listOf(
                    SubcommandGroupDefinition(
                        name = "search",
                        description = "Search",
                        subcommands = listOf(SubcommandDefinition(name = "name", description = "By name")),
                    ),
                ),
            )
        }

        assertTrue(
            failure.message.orEmpty().contains("must not declare a top-level string option"),
            "the failure did not explain the structural rule: ${failure.message}",
        )
    }

    @Test
    fun `Given a subcommand definition When built Then default to a required-free option and no translations`() {
        val leaf = SubcommandDefinition(name = "list", description = "List")

        assertNull(leaf.stringOption)
        assertEquals(mapOf<LocaleCode, String>(), leaf.descriptionLocalizations)
    }

    @Test
    fun `Given a plain command context When built Then both subcommand segments are null`() {
        val context = CommandContext(
            commandName = "queue",
            guildId = GuildId(1uL),
            channelId = ChannelId(2uL),
            userId = UserId(3uL),
            locale = null,
        )

        assertNull(context.subcommandGroup)
        assertNull(context.subcommand)
    }

    @Test
    fun `Given a group invocation When copied with segments Then carry the group and the leaf`() {
        val context = CommandContext(
            commandName = "radio",
            guildId = GuildId(1uL),
            channelId = ChannelId(2uL),
            userId = UserId(3uL),
            locale = null,
        )

        val group = context.copy(subcommandGroup = "search", subcommand = "name")

        assertEquals("search", group.subcommandGroup)
        assertEquals("name", group.subcommand)
        assertNull(context.subcommandGroup, "copying must not mutate the plain context")
    }
}
