package es.wokis.commands.sounds

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ActionRowView
import es.wokis.message.ButtonStyle
import es.wokis.message.ButtonView
import es.wokis.message.ComponentId
import es.wokis.message.EmbedFieldView
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import es.wokis.player.message.PLAYER_BRAND_COLOR

private const val PREVIOUS_EMOJI = "⬅"
private const val NEXT_EMOJI = "➡"
private const val FIRST_PAGE = 1

/**
 * Builds the neutral `/sounds` view: Kord's paginated embed with three inline name columns, the
 * "Page X out of Y" footer and the previous/next buttons, which are emitted only when needed.
 */
class SoundsMessageViewFactory(
    private val localizationService: GuildLocalizationService,
) {

    suspend fun createView(
        soundNames: List<String>,
        page: Int,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
    ): MessageView {
        val pages = soundPages(soundNames)
        val currentPage = page.coerceIn(FIRST_PAGE, pages.size.coerceAtLeast(FIRST_PAGE))
        val columns = pages.getOrNull(currentPage - 1).orEmpty()
        return MessageView(
            embed = EmbedView(
                title = getString(PlayerMessageKeys.SOUNDS_EMBED_TITLE, guildId, guildDefaultLocale),
                description = getStringFormat(
                    key = PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION,
                    guildId = guildId,
                    guildDefaultLocale = guildDefaultLocale,
                    arguments = arrayOf<Any>(soundNames.size),
                ),
                color = PLAYER_BRAND_COLOR,
                fields = if (columns.isEmpty()) {
                    emptyList()
                } else {
                    (0 until SOUND_COLUMNS).map { index ->
                        EmbedFieldView(
                            name = SOUND_COLUMN_NAME,
                            value = columns.getOrNull(index)?.takeIf(String::isNotEmpty)
                                ?: SOUND_COLUMN_NAME,
                            inline = true,
                        )
                    }
                },
                footer = pages.takeIf { it.isNotEmpty() }?.let {
                    getStringFormat(
                        key = PlayerMessageKeys.PAGINATED_EMBED_FOOTER,
                        guildId = guildId,
                        guildDefaultLocale = guildDefaultLocale,
                        arguments = arrayOf<Any>(currentPage, it.size),
                    )
                },
            ),
            rows = if (pages.size > 1) {
                listOf(paginationRow(currentPage, pages.size, guildId, guildDefaultLocale))
            } else {
                emptyList()
            },
        )
    }

    private suspend fun paginationRow(
        currentPage: Int,
        pageCount: Int,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
    ): ActionRowView = ActionRowView(
        buttons = listOf(
            ButtonView(
                componentId = ComponentId.SOUNDS_PREVIOUS,
                label = getString(PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL, guildId, guildDefaultLocale),
                emoji = PREVIOUS_EMOJI,
                style = ButtonStyle.SECONDARY,
                disabled = currentPage == FIRST_PAGE,
                payload = currentPage.toString(),
            ),
            ButtonView(
                componentId = ComponentId.SOUNDS_NEXT,
                label = getString(PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL, guildId, guildDefaultLocale),
                emoji = NEXT_EMOJI,
                style = ButtonStyle.SECONDARY,
                disabled = currentPage == pageCount,
                payload = currentPage.toString(),
            ),
        ),
    )

    private suspend fun getString(key: String, guildId: GuildId?, guildDefaultLocale: LocaleCode?): String =
        localizationService.getString(key = key, guildId = guildId, guildDefaultLocale = guildDefaultLocale)

    private suspend fun getStringFormat(
        key: String,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
        vararg arguments: Any,
    ): String = localizationService.getStringFormat(
        key = key,
        guildId = guildId,
        guildDefaultLocale = guildDefaultLocale,
        arguments = arguments,
    )
}
