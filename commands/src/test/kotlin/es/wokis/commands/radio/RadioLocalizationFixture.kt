package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.NoPersistedLocale
import es.wokis.commands.player.commandContext
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.localization.RadioMessageKeys
import es.wokis.message.ComponentId
import es.wokis.message.MessageView
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

/**
 * Deterministic radio localization: every key resolves to a sentinel naming it, so asserting the
 * reply text proves which catalog key the handler selected. A second locale is preloaded to prove
 * the invoking guild locale is honoured.
 */
internal class RadioLocalizationFixture {

    val service: GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(NoPersistedLocale),
        catalog = LocalizationCatalog.of(
            TEMPLATES.mapValues { (_, english) ->
                mapOf(ENGLISH_US to english, SPANISH to "$SPANISH_SENTINEL$english")
            },
        ),
    )

    fun english(key: String): String = TEMPLATES.getValue(key)

    fun englishFormat(key: String, vararg arguments: Any): String = english(key).format(*arguments)

    fun spanish(key: String): String = "$SPANISH_SENTINEL${english(key)}"

    fun spanishFormat(key: String, vararg arguments: Any): String = spanish(key).format(*arguments)

    private companion object {
        val ENGLISH_US = LocaleCode("en-US")
        val SPANISH = LocaleCode("es-ES")
        const val SPANISH_SENTINEL = "es|"

        val DESCRIPTION_KEYS = listOf(
            RadioMessageKeys.RADIO_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_PLAY_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_PLAY_INPUT_DESCRIPTION,
            RadioMessageKeys.RADIO_LIST_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_NAME_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_NAME_INPUT_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_INPUT_DESCRIPTION,
            RadioMessageKeys.RADIO_RANDOM_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION,
        )

        val RESPONSE_TEMPLATES = mapOf(
            PlayerMessageKeys.ERROR_NO_GUILD to "no guild",
            PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL to "no voice channel",
            RadioMessageKeys.RADIO_UNKNOWN_MESSAGE to "unknown radio option",
            RadioMessageKeys.RADIO_PLAY_FOUND to "radio found",
            RadioMessageKeys.RADIO_PLAY_NOT_FOUND to "radio not found",
            RadioMessageKeys.RADIO_PLAY_REQUIRED to "radio name required",
            RadioMessageKeys.RADIO_RANDOM_SEARCHING to "searching a random station",
            RadioMessageKeys.RADIO_RANDOM_ERROR to "random station failed",
            RadioMessageKeys.RADIO_COUNTRYCODES_EMPTY to "no country codes",
            RadioMessageKeys.RADIO_COUNTRYCODES_ERROR to "country codes failed",
            RadioMessageKeys.RADIO_LIST_EMBED_TITLE to "radio list title",
            RadioMessageKeys.RADIO_LIST_EMBED_DESCRIPTION to "radio list description",
            RadioMessageKeys.RADIO_COUNTRYCODES_EMBED_TITLE to "country codes title",
            PlayerMessageKeys.PAGINATED_EMBED_FOOTER to "Page %d out of %d",
            PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL to "previous label",
            PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL to "next label",
        )

        val TEMPLATES: Map<String, String> = DESCRIPTION_KEYS.associateWith { "description of $it" } +
            RESPONSE_TEMPLATES
    }
}

/** One station with derivable presentation fields, so reply/play assertions stay exact. */
internal fun radioStation(
    name: String,
    countryCode: String = "ES",
): RadioStation = RadioStation(
    name = name,
    streamUrl = "https://stream.example/$name",
    faviconUrl = "https://favicon.example/$name.png",
    countryCode = countryCode,
)

internal fun radioPage(
    currentPage: Int,
    maxPage: Int,
    stations: List<RadioStation>,
): RadioPage = RadioPage(currentPage = currentPage, maxPage = maxPage, stations = stations)

/** Text command context with the default guild, optionally carrying subcommand segments. */
internal fun radioContext(
    options: Map<String, String> = emptyMap(),
    subcommandGroup: String? = null,
    subcommand: String? = null,
    guildId: GuildId? = GuildId(RADIO_GUILD_ID.toULong()),
    guildLocale: LocaleCode? = null,
): CommandContext = commandContext(guildId = guildId, guildLocale = guildLocale, options = options).copy(
    commandName = RadioCommandDefinitions.RADIO_COMMAND_NAME,
    subcommandGroup = subcommandGroup,
    subcommand = subcommand,
)

internal fun radioComponentContext(
    componentId: ComponentId,
    payload: String?,
    guildLocale: LocaleCode? = null,
): ComponentContext = ComponentContext(
    componentId = componentId,
    guildId = GuildId(RADIO_GUILD_ID.toULong()),
    channelId = ChannelId(RADIO_CHANNEL_ID.toULong()),
    userId = UserId(RADIO_USER_ID.toULong()),
    guildLocale = guildLocale,
    payload = payload,
)

/** Strict responder for one component interaction: both gateway calls must be verified. */
internal fun strictComponentResponder(): ComponentResponder = mockk<ComponentResponder>().also { responder ->
    coJustRun { responder.deferEdit() }
    coJustRun { responder.edit(any()) }
}

/** The single view a component handler edited. */
internal fun editedView(responder: ComponentResponder): MessageView {
    val edited = slot<MessageView>()
    coVerify(exactly = 1) { responder.edit(capture(edited)) }
    return edited.captured
}

internal const val RADIO_GUILD_ID = 42L
internal const val RADIO_CHANNEL_ID = 43L
internal const val RADIO_USER_ID = 44L
