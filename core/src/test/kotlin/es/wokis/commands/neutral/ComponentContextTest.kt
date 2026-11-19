package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.message.ComponentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComponentContextTest {

    @Test
    fun `Given equal component contexts When compared Then use data class equality`() {
        // Given
        val context = context()
        val same = context()

        // When / Then
        assertEquals(context, same)
        assertEquals(context.hashCode(), same.hashCode())
    }

    @Test
    fun `Given a changed id or locale When compared Then the context differs`() {
        // Given
        val context = context()

        // When / Then
        assertNotEquals(context, context(componentId = ComponentId.PLAYER_SKIP))
        assertNotEquals(context, context(guildId = GuildId(11uL)))
        assertNotEquals(context, context(channelId = ChannelId(21uL)))
        assertNotEquals(context, context(userId = UserId(31uL)))
        assertNotEquals(context, context(locale = LocaleCode("de")))
    }

    @Test
    fun `Given a context without locales When inspected Then the optional locale fields are null`() {
        // Given
        val context = context()

        // When / Then
        assertNull(context.locale)
        assertNull(context.guildLocale)
        assertNull(context.userLocale)
    }

    @Test
    fun `Given guild and user locales When inspected Then keep them separate from the resolved locale`() {
        // Given: the guild default wins over the client preference in the resolved locale
        val context = context(
            locale = LocaleCode("es-ES"),
            guildLocale = LocaleCode("es-ES"),
            userLocale = LocaleCode("de"),
        )

        // When / Then
        assertEquals(LocaleCode("es-ES"), context.locale)
        assertEquals(LocaleCode("es-ES"), context.guildLocale)
        assertEquals(LocaleCode("de"), context.userLocale)
    }

    @Test
    fun `Given a direct message context When inspected Then the guild id is null`() {
        // Given
        val context = context(guildId = null, locale = LocaleCode("es-419"), userLocale = LocaleCode("es-419"))

        // When / Then
        assertNull(context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
        assertEquals(LocaleCode("es-419"), context.locale)
    }

    @Test
    fun `Given a copied context When a field is replaced Then the original is unchanged`() {
        // Given
        val original = context()
        val copy = original.copy(guildId = GuildId(99uL))

        // When / Then
        assertEquals(GuildId(10uL), original.guildId)
        assertEquals(GuildId(99uL), copy.guildId)
        assertEquals(original.componentId, copy.componentId)
    }

    private fun context(
        componentId: ComponentId = ComponentId.PLAYER_PAUSE,
        guildId: GuildId? = GuildId(10uL),
        channelId: ChannelId = ChannelId(20uL),
        userId: UserId = UserId(30uL),
        locale: LocaleCode? = null,
        guildLocale: LocaleCode? = null,
        userLocale: LocaleCode? = null,
    ): ComponentContext = ComponentContext(
        componentId = componentId,
        guildId = guildId,
        channelId = channelId,
        userId = userId,
        locale = locale,
        guildLocale = guildLocale,
        userLocale = userLocale,
    )
}
