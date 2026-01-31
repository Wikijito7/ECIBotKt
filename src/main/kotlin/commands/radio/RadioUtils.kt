package es.wokis.commands.radio

import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import es.wokis.commands.commons.createPaginatedEmbedMessage
import es.wokis.constants.BLANK_SPACE
import es.wokis.data.radio.RadioDTO
import es.wokis.data.radio.RadioPageDTO
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.orDefaultLocale
import es.wokis.utils.takeAtMost

private const val RADIO_LIST_COLUMNS = 3

fun List<RadioDTO>.chunked(columns: Int): List<String> = map {
    (if (it.radioName.contains(Regex("^[#*-]"))) "\\" else "").plus(it.radioName.takeAtMost(20))
}.chunked(size / columns).map {
    it.joinToString(separator = "$BLANK_SPACE$BLANK_SPACE\n")
}

suspend fun onExecuteRadioListCommand(
    interaction: ChatInputCommandInteraction,
    currentRadioPage: RadioPageDTO?,
    response: DeferredPublicMessageInteractionResponseBehavior,
    previousButtonCustomId: String,
    nextButtonCustomId: String,
    localizationService: LocalizationService
) {
    val locale = interaction.locale.orDefaultLocale()
    val radioPageContent = currentRadioPage?.radios?.chunked(RADIO_LIST_COLUMNS)
    val maxPages = currentRadioPage?.maxPage ?: 1
    response.respond {
        createPaginatedEmbedMessage(
            locale = locale,
            localizationService = localizationService,
            title = "Radio List",
            description = "List of radios available",
            currentPage = 1,
            currentPageContent = radioPageContent,
            columns = RADIO_LIST_COLUMNS,
            pageCount = maxPages,
            previousButtonCustomId = previousButtonCustomId,
            nextButtonCustomId = nextButtonCustomId
        )
    }
}

suspend fun onInteractRadioListCommand(
    interaction: ComponentInteraction,
    localizationService: LocalizationService,
    previousButtonCustomId: String,
    nextButtonCustomId: String,
    block: suspend (Int) -> RadioPageDTO?
) {
    val locale = interaction.locale.orDefaultLocale()
    val customId = (interaction as? ButtonInteraction)?.component?.customId
    val updatePageBy = if (customId == nextButtonCustomId) 1 else -1
    val footerSplit = interaction.message.embeds.firstOrNull()
        ?.footer?.text?.split(" ")
    val newPage = footerSplit?.get(1)?.toIntOrNull()?.plus(updatePageBy) ?: 1
    val newRadioPage = block(newPage)
    val currentPage = newRadioPage?.currentPage ?: 1
    val maxPage = newRadioPage?.maxPage ?: 1
    val radioPageContent = newRadioPage?.radios?.chunked(RADIO_LIST_COLUMNS)
    interaction.message.edit {
        createPaginatedEmbedMessage(
            locale = locale,
            localizationService = localizationService,
            title = "Radio List",
            description = "List of radios available",
            currentPage = currentPage,
            currentPageContent = radioPageContent,
            columns = RADIO_LIST_COLUMNS,
            pageCount = maxPage,
            previousButtonCustomId = previousButtonCustomId,
            nextButtonCustomId = nextButtonCustomId
        )
    }
}
