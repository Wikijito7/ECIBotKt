package es.wokis.commands.radio

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.localization.RadioMessageKeys
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
private const val BLANK_FIELD_VALUE = "\u2800"
private const val MAX_FIELD_LENGTH = 1000
private const val MAX_FIELDS = 25
private const val ITEMS_PER_LINE = 8
private const val REGIONAL_INDICATOR_A_CODE_POINT = 0x1F1E6
private const val UNKNOWN_COUNTRY_CODE = "UNK"
private const val UNKNOWN_COUNTRY_FLAG = "❓"

/**
 * Builds the neutral radio views, mirroring Kord's paginated embed helpers.
 *
 * `/radio list` and both searches share one layout (three inline name columns, page footer and
 * previous/next buttons). `/radio countrycodes` keeps Kord's flag fields and additionally emits the
 * pagination buttons the accepted paired routing fix requires.
 */
class RadioMessageViewFactory(
    private val localizationService: GuildLocalizationService,
) {

    suspend fun createListPage(
        stations: List<RadioStation>,
        request: RadioPageRequest,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
    ): MessageView {
        val columns = radioColumns(stations)
        return MessageView(
            embed = EmbedView(
                title = getString(RadioMessageKeys.RADIO_LIST_EMBED_TITLE, guildId, guildDefaultLocale),
                description = getString(RadioMessageKeys.RADIO_LIST_EMBED_DESCRIPTION, guildId, guildDefaultLocale),
                color = PLAYER_BRAND_COLOR,
                fields = if (columns.isEmpty()) {
                    emptyList()
                } else {
                    (0 until RADIO_LIST_COLUMNS).map { index ->
                        EmbedFieldView(
                            name = BLANK_FIELD_VALUE,
                            value = columns.getOrNull(index).orEmpty().ifEmpty { BLANK_FIELD_VALUE },
                            inline = true,
                        )
                    }
                },
                footer = pageFooter(request.currentPage, request.pageCount, guildId, guildDefaultLocale),
            ),
            rows = if (request.pageCount > 1) {
                listOf(
                    ActionRowView(
                        buttons = listOf(
                            ButtonView(
                                componentId = request.previous,
                                label = getString(
                                    PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL,
                                    guildId,
                                    guildDefaultLocale,
                                ),
                                emoji = PREVIOUS_EMOJI,
                                style = ButtonStyle.SECONDARY,
                                disabled = request.currentPage <= FIRST_RADIO_PAGE,
                                payload = radioPagePayload(request.currentPage, request.query),
                            ),
                            ButtonView(
                                componentId = request.next,
                                label = getString(
                                    PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL,
                                    guildId,
                                    guildDefaultLocale,
                                ),
                                emoji = NEXT_EMOJI,
                                style = ButtonStyle.SECONDARY,
                                disabled = request.currentPage >= request.pageCount,
                                payload = radioPagePayload(request.currentPage, request.query),
                            ),
                        ),
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    suspend fun createCountryCodesPage(
        countryCodes: List<String>,
        currentPage: Int,
        pageCount: Int,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
    ): MessageView = MessageView(
        embed = EmbedView(
            title = getString(RadioMessageKeys.RADIO_COUNTRYCODES_EMBED_TITLE, guildId, guildDefaultLocale),
            color = PLAYER_BRAND_COLOR,
            fields = groupCountryCodesToFields(countryCodes).map { field ->
                EmbedFieldView(name = BLANK_FIELD_VALUE, value = field, inline = false)
            },
            footer = if (pageCount > 1) {
                pageFooter(currentPage, pageCount, guildId, guildDefaultLocale)
            } else {
                null
            },
        ),
        rows = if (pageCount > 1) {
            listOf(countryCodesPaginationRow(currentPage, pageCount, guildId, guildDefaultLocale))
        } else {
            emptyList()
        },
    )

    private suspend fun countryCodesPaginationRow(
        currentPage: Int,
        pageCount: Int,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
    ): ActionRowView = ActionRowView(
        buttons = listOf(
            ButtonView(
                componentId = ComponentId.RADIO_COUNTRYCODES_PREVIOUS,
                label = getString(
                    PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL,
                    guildId,
                    guildDefaultLocale,
                ),
                emoji = PREVIOUS_EMOJI,
                style = ButtonStyle.SECONDARY,
                disabled = currentPage <= FIRST_RADIO_PAGE,
                payload = currentPage.toString(),
            ),
            ButtonView(
                componentId = ComponentId.RADIO_COUNTRYCODES_NEXT,
                label = getString(
                    PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL,
                    guildId,
                    guildDefaultLocale,
                ),
                emoji = NEXT_EMOJI,
                style = ButtonStyle.SECONDARY,
                disabled = currentPage >= pageCount,
                payload = currentPage.toString(),
            ),
        ),
    )

    private suspend fun pageFooter(
        currentPage: Int,
        pageCount: Int,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
    ): String = localizationService.getStringFormat(
        key = PlayerMessageKeys.PAGINATED_EMBED_FOOTER,
        guildId = guildId,
        guildDefaultLocale = guildDefaultLocale,
        arguments = arrayOf<Any>(currentPage, pageCount),
    )

    private suspend fun getString(key: String, guildId: GuildId?, guildDefaultLocale: LocaleCode?): String =
        localizationService.getString(key = key, guildId = guildId, guildDefaultLocale = guildDefaultLocale)
}

/** Kord's country-code field layout: one flag per code, eight per line, inside embed limits. */
private fun groupCountryCodesToFields(countryCodes: List<String>): List<String> {
    if (countryCodes.isEmpty()) return emptyList()

    val formattedCodes = countryCodes.map { code -> "${flagEmoji(code)} $code" }
    val fields = mutableListOf<String>()
    var currentField = StringBuilder()

    formattedCodes.forEachIndexed { index, code ->
        val isLastItem = index == formattedCodes.lastIndex
        val fitsLine = (index + 1) % ITEMS_PER_LINE == 0 || isLastItem
        val separator = if (fitsLine) "" else BLANK_FIELD_VALUE
        val nextLength = currentField.length + code.length + separator.length +
            if ((index + 1) % ITEMS_PER_LINE == 0) 1 else 0

        if (nextLength > MAX_FIELD_LENGTH && currentField.isNotEmpty()) {
            fields.add(currentField.toString())
            currentField = StringBuilder()
            if (fields.size >= MAX_FIELDS) {
                return fields
            }
        }

        currentField.append(code)
        if (!isLastItem) {
            currentField.append(if ((index + 1) % ITEMS_PER_LINE == 0) "\n" else separator)
        }
    }

    if (currentField.isNotEmpty() && fields.size < MAX_FIELDS) {
        fields.add(currentField.toString())
    }
    return fields
}

/** Kord's regional-indicator flag for a two-letter code, or a question mark for `UNK`. */
private fun flagEmoji(countryCode: String): String = if (countryCode == UNKNOWN_COUNTRY_CODE) {
    UNKNOWN_COUNTRY_FLAG
} else {
    countryCode.uppercase().map { character ->
        Character.toChars(REGIONAL_INDICATOR_A_CODE_POINT + (character - 'A')).joinToString("")
    }.joinToString("")
}
