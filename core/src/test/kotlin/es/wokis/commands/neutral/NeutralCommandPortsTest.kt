package es.wokis.commands.neutral

import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocaleMessageKeys
import es.wokis.localization.LocalizationCatalog
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
        GuildId::class.java,
        ChannelId::class.java,
        UserId::class.java,
        LocaleCode::class.java,
        DiscordLocaleCatalog::class.java,
        LocaleRepository::class.java,
        GetGuildLocaleUseCase::class.java,
        SetGuildLocaleUseCase::class.java,
        GuildLocalizationService::class.java,
        LocalizationCatalog::class.java,
        LocaleMessageKeys::class.java,
    )

    private val gatewayPrefixes = listOf(
        "net.dv8tion.",
        "dev.kord.",
        "discord.",
    )

    @Test
    fun `Given neutral command ports When inspected Then expose no gateway types`() {
        val offenders = neutralTypes.flatMap { type -> gatewayReferences(type) }

        assertTrue(offenders.isEmpty(), "Gateway types leaked into the neutral boundary: $offenders")
    }

    private fun gatewayReferences(type: Class<*>): List<String> {
        val references = mutableListOf<String>()

        type.declaredFields.forEach { field ->
            references += type.referenceFor(field.name, field.type.typeName)
            references += type.referenceFor(field.name, field.genericType.typeName)
        }

        type.declaredMethods.forEach { method ->
            references += type.referenceFor(method.name, method.returnType.typeName, "()")
            references += type.referenceFor(method.name, method.genericReturnType.typeName, "()")
            method.parameterTypes.forEach { parameter ->
                references += type.parameterReference(method.name, parameter.typeName)
            }
            method.genericParameterTypes.forEach { parameter ->
                references += type.parameterReference(method.name, parameter.typeName)
            }
        }

        type.declaredConstructors.forEach { constructor ->
            constructor.parameterTypes.forEach { parameter ->
                references += type.parameterReference("<init>", parameter.typeName)
            }
            constructor.genericParameterTypes.forEach { parameter ->
                references += type.parameterReference("<init>", parameter.typeName)
            }
        }

        return references.filter { it.isNotEmpty() }
    }

    private fun Class<*>.referenceFor(memberName: String, candidate: String, memberSuffix: String = ""): String =
        candidate.takeIf { it.isGatewayType() }?.let { "$simpleName.$memberName$memberSuffix: $it" }.orEmpty()

    private fun Class<*>.parameterReference(memberName: String, candidate: String): String =
        candidate.takeIf { it.isGatewayType() }?.let { "$simpleName.$memberName($it)" }.orEmpty()

    private fun String.isGatewayType(): Boolean = gatewayPrefixes.any { startsWith(it) }
}
