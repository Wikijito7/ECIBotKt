package es.wokis.commands.player

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

/** Kord's invisible field name keeps the single paginated queue column aligned. */
private const val QUEUE_FIELD_NAME = "\u2800"

private const val PREVIOUS_EMOJI = "⬅"
private const val NEXT_EMOJI = "➡"
private const val FIRST_PAGE = 1

/**
 * Builds the neutral `/queue` view: Kord's paginated embed with one inline track column, the
 * "Page X out of Y" footer and the previous/next buttons, which are emitted only when needed.
 */
class QueueMessageViewFactory(
    private val localizationService: GuildLocalizationService,
) {

    suspend fun createView(
        queue: PlayerQueueSnapshot,
        page: Int,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
    ): MessageView {
        val pages = queuePages(queue.snapshot.queue)
        val currentPage = page.coerceIn(FIRST_PAGE, pages.size.coerceAtLeast(FIRST_PAGE))
        val pageContent = pages.getOrNull(currentPage - 1)
        return MessageView(
            embed = EmbedView(
                title = getString(PlayerMessageKeys.QUEUE_EMBED_TITLE, guildId, guildDefaultLocale),
                description = getStringFormat(
                    key = PlayerMessageKeys.QUEUE_EMBED_DESCRIPTION,
                    guildId = guildId,
                    guildDefaultLocale = guildDefaultLocale,
                    arguments = arrayOf<Any>(queue.snapshot.queue.size, queue.guildName),
                ),
                color = PLAYER_BRAND_COLOR,
                fields = pageContent?.let {
                    listOf(EmbedFieldView(name = QUEUE_FIELD_NAME, value = it, inline = true))
                }.orEmpty(),
                footer = pages.takeIf { it.isNotEmpty() }?.let {
                    getStringFormat(
                        key = PlayerMessageKeys.PAGINATED_EMBED_FOOTER,
                        guildId = guildId,
                        guildDefaultLocale = guildDefaultLocale,
                        arguments = arrayOf(currentPage, it.size),
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
                componentId = ComponentId.QUEUE_PREVIOUS,
                label = getString(PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL, guildId, guildDefaultLocale),
                emoji = PREVIOUS_EMOJI,
                style = ButtonStyle.SECONDARY,
                disabled = currentPage == FIRST_PAGE,
                payload = currentPage.toString(),
            ),
            ButtonView(
                componentId = ComponentId.QUEUE_NEXT,
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
