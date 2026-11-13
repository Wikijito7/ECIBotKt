package es.wokis.commands.neutral

import es.wokis.commands.walkthrough.WalkthroughCommand
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NeutralCommandPortsTest {

    private val neutralTypes = listOf(
        CommandContext::class.java,
        CommandDefinition::class.java,
        CommandHandler::class.java,
        CommandResponder::class.java,
        CommandResponse::class.java,
        GuildCommandRegistrar::class.java,
        WalkthroughCommand::class.java,
        GuildId::class.java,
        ChannelId::class.java,
        UserId::class.java,
        LocaleCode::class.java,
    )

    private val gatewayPrefixes = listOf(
        "net.dv8tion.",
        "dev.kord.",
        "discord.",
    )

    @Test
    fun `Given neutral command ports When inspected Then expose no gateway types`() {
        // When
        val offenders = neutralTypes.flatMap { type -> gatewayReferences(type) }

        // Then
        assertTrue(offenders.isEmpty(), "Gateway types leaked into the neutral boundary: $offenders")
    }

    @Test
    fun `Given a plain fake responder When walkthrough command runs Then the port needs no gateway implementation`() =
        runTest {
            // Given
            val responder = PlainResponder()
            val context = CommandContext(
                commandName = WalkthroughCommand.COMMAND_NAME,
                guildId = GuildId(1uL),
                channelId = ChannelId(2uL),
                userId = UserId(3uL),
                locale = LocaleCode("en-US"),
            )

            // When
            WalkthroughCommand().handle(context, responder)

            // Then
            assertEquals(
                "JDA walkthrough OK | guild=1 | channel=2 | user=3 | locale=en-US",
                responder.responses.single().content,
            )
            assertTrue(responder.responses.single().ephemeral)
            assertTrue(responder.deferCalls.isEmpty())
        }

    private fun gatewayReferences(type: Class<*>): List<String> {
        val references = mutableListOf<String>()

        type.declaredFields.forEach { field ->
            field.type.typeName
                .takeIf { it.isGatewayType() }
                ?.let { references += "${type.simpleName}.${field.name}: $it" }
            field.genericType.typeName
                .takeIf { it.isGatewayType() }
                ?.let { references += "${type.simpleName}.${field.name}: $it" }
        }

        type.declaredMethods.forEach { method ->
            method.returnType.typeName
                .takeIf { it.isGatewayType() }
                ?.let { references += "${type.simpleName}.${method.name}(): $it" }
            method.genericReturnType.typeName
                .takeIf { it.isGatewayType() }
                ?.let { references += "${type.simpleName}.${method.name}(): $it" }
            method.parameterTypes.forEach { parameter ->
                parameter.typeName
                    .takeIf { it.isGatewayType() }
                    ?.let { references += "${type.simpleName}.${method.name}($it)" }
            }
            method.genericParameterTypes.forEach { parameter ->
                parameter.typeName
                    .takeIf { it.isGatewayType() }
                    ?.let { references += "${type.simpleName}.${method.name}($it)" }
            }
        }

        type.declaredConstructors.forEach { constructor ->
            constructor.parameterTypes.forEach { parameter ->
                parameter.typeName
                    .takeIf { it.isGatewayType() }
                    ?.let { references += "${type.simpleName}.<init>($it)" }
            }
            constructor.genericParameterTypes.forEach { parameter ->
                parameter.typeName
                    .takeIf { it.isGatewayType() }
                    ?.let { references += "${type.simpleName}.<init>($it)" }
            }
        }

        return references
    }

    private fun String.isGatewayType(): Boolean = gatewayPrefixes.any { startsWith(it) }

    private class PlainResponder : CommandResponder {
        val deferCalls = mutableListOf<Boolean>()
        val responses = mutableListOf<CommandResponse>()

        override suspend fun defer(ephemeral: Boolean) {
            deferCalls += ephemeral
        }

        override suspend fun respond(response: CommandResponse) {
            responses += response
        }
    }
}
