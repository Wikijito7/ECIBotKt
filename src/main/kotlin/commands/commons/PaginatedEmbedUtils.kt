package es.wokis.commands.commons

import dev.kord.common.Color
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.AbstractMessageModifyBuilder
import es.wokis.constants.BLANK_SPACE
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.takeIfNotEmpty

fun AbstractMessageModifyBuilder.createPaginatedEmbedMessage(
    locale: Locale,
    localizationService: LocalizationService,
    title: String,
    description: String?,
    currentPage: Int,
    currentPageContent: List<String>?,
    columns: Int,
    pageCount: Int,
    previousButtonCustomId: String,
    nextButtonCustomId: String
) {
    createEmbed(
        locale = locale,
        localizationService = localizationService,
        embedTitle = title,
        embedDescription = description,
        currentPage = currentPage,
        currentPageContent = currentPageContent,
        columns = columns,
        pageCount = pageCount
    )
    if (pageCount > 1) {
        components = getMessageComponentBuilders(
            locale = locale,
            localizationService = localizationService,
            previousButtonCustomId = previousButtonCustomId,
            nextButtonCustomId = nextButtonCustomId,
            disablePrevious = currentPage == 1,
            disableNext = currentPage == pageCount
        )
    }
}

private fun AbstractMessageModifyBuilder.createEmbed(
    locale: Locale,
    localizationService: LocalizationService,
    embedTitle: String,
    embedDescription: String?,
    currentPage: Int,
    currentPageContent: List<String>?,
    columns: Int,
    pageCount: Int
) {
    embed {
        title = embedTitle
        embedDescription?.let {
            description = it
        }
        color = Color(0x01B05B)
        if (currentPageContent.isNullOrEmpty().not()) {
            for (column in (0 until columns)) {
                val displayMessage = currentPageContent?.getOrNull(column)?.takeIfNotEmpty() ?: BLANK_SPACE
                field {
                    name = BLANK_SPACE
                    value = displayMessage
                    inline = true
                }
            }
        }
        if (pageCount > 0) {
            footer {
                text = localizationService.getStringFormat(
                    key = LocalizationKeys.PAGINATED_EMBED_FOOTER,
                    locale = locale,
                    arguments = arrayOf(currentPage, pageCount)
                )
            }
        }
    }
}

private fun getMessageComponentBuilders(
    locale: Locale,
    localizationService: LocalizationService,
    previousButtonCustomId: String,
    nextButtonCustomId: String,
    disablePrevious: Boolean,
    disableNext: Boolean
): MutableList<MessageComponentBuilder> =
    mutableListOf(
        ActionRowBuilder().apply {
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = previousButtonCustomId
            ) {
                label = localizationService.getString(LocalizationKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL, locale)
                emoji = DiscordPartialEmoji(name = "⬅")
                disabled = disablePrevious
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = nextButtonCustomId
            ) {
                label = localizationService.getString(LocalizationKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL, locale)
                emoji = DiscordPartialEmoji(name = "➡")
                disabled = disableNext
            }
        }
    )