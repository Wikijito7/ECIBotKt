package es.wokis.commands.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.ViewCommandResponder
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.MessageView
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coJustRun
import io.mockk.mockk

internal const val GUILD_ID = 42L
internal const val CHANNEL_ID = 43L
internal const val USER_ID = 44L

/**
 * Deterministic localization for the command tests.
 *
 * Every key resolves to a sentinel naming it, so asserting the reply text proves which catalog key
 * the handler selected. A second locale is preloaded to prove the invoking guild locale is honoured.
 */
internal class PlayerLocalizationFixture {

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

    fun spanishFormat(key: String, vararg arguments: Any): String = spanish(key).format(*arguments)

    fun spanish(key: String): String = "$SPANISH_SENTINEL${english(key)}"

    private companion object {
        val ENGLISH_US = LocaleCode("en-US")
        val SPANISH = LocaleCode("es-ES")
        const val SPANISH_SENTINEL = "es|"

        val DESCRIPTION_KEYS = listOf(
            PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION,
            PlayerMessageKeys.PLAY_COMMAND_INPUT_DESCRIPTION,
            PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION,
            PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION,
            PlayerMessageKeys.SKIP_COMMAND_DESCRIPTION,
            PlayerMessageKeys.SHUFFLE_COMMAND_DESCRIPTION,
            PlayerMessageKeys.NEXT_COMMAND_DESCRIPTION,
            PlayerMessageKeys.NEXT_COMMAND_INPUT_DESCRIPTION,
            PlayerMessageKeys.QUEUE_COMMAND_DESCRIPTION,
            PlayerMessageKeys.DISCONNECT_COMMAND_DESCRIPTION,
            PlayerMessageKeys.RECONNECT_COMMAND_DESCRIPTION,
            PlayerMessageKeys.SOUND_COMMAND_DESCRIPTION,
            PlayerMessageKeys.SOUND_COMMAND_INPUT_DESCRIPTION,
            PlayerMessageKeys.SOUNDS_DESCRIPTION,
            PlayerMessageKeys.TTS_COMMAND_DESCRIPTION,
            PlayerMessageKeys.TTS_COMMAND_INPUT_DESCRIPTION,
        )

        val RESPONSE_TEMPLATES = mapOf(
            PlayerMessageKeys.SKIP_COMMAND_RESPONSE to "track skipped",
            PlayerMessageKeys.SHUFFLE_COMMAND_RESPONSE to "queue shuffled",
            PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE to "disconnected",
            PlayerMessageKeys.RECONNECT_SUCCESS to "reconnected",
            PlayerMessageKeys.RECONNECT_NOT_CONNECTED to "not connected",
            PlayerMessageKeys.PLAYER_RECONNECT_FAILED to "reconnect failed",
            PlayerMessageKeys.SEARCHING_SONG to "searching",
            PlayerMessageKeys.NO_MATCHES to "no matches for %s",
            PlayerMessageKeys.NEXT_EMPTY_QUEUE to "queue empty",
            PlayerMessageKeys.NEXT_TRACK_MOVED to "moved %s next",
            PlayerMessageKeys.NEXT_TRACK_NOT_IN_QUEUE_TRYING_SEARCH to "missing %s, searching",
            PlayerMessageKeys.ERROR_NO_GUILD to "no guild",
            PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL to "no voice channel",
            PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED to "%s is required",
            PlayerMessageKeys.QUEUE_EMBED_TITLE to "queue title",
            PlayerMessageKeys.QUEUE_EMBED_DESCRIPTION to "%d tracks in %s's queue",
            PlayerMessageKeys.SOUNDS_EMBED_TITLE to "sounds title",
            PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION to "%d sounds available",
            PlayerMessageKeys.TTS_COMMAND_RESPONSE to "tts queued",
            PlayerMessageKeys.PAGINATED_EMBED_FOOTER to "Page %d out of %d",
            PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL to "previous label",
            PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL to "next label",
        )

        val TEMPLATES: Map<String, String> = DESCRIPTION_KEYS.associateWith { "description of $it" } +
            RESPONSE_TEMPLATES
    }
}

/** Strict responder for text replies: every call must be stubbed or verified by the test. */
internal fun strictResponder(): CommandResponder = mockk<CommandResponder>().also { responder ->
    coJustRun { responder.defer(any()) }
    coJustRun { responder.respond(any()) }
}

/** Records text and view replies so a handler can be inspected without a gateway. */
internal class RecordingCommandResponder : CommandResponder, ViewCommandResponder {

    val responses = mutableListOf<CommandResponse>()
    val views = mutableListOf<MessageView>()
    var deferrals = 0

    override suspend fun defer(ephemeral: Boolean) {
        deferrals++
    }

    override suspend fun respond(response: CommandResponse) {
        responses += response
    }

    override suspend fun respond(view: MessageView) {
        views += view
    }
}

internal fun commandContext(
    guildId: GuildId? = GuildId(GUILD_ID.toULong()),
    guildLocale: LocaleCode? = null,
    options: Map<String, String> = emptyMap(),
): CommandContext = CommandContext(
    commandName = "test-command",
    guildId = guildId,
    channelId = ChannelId(CHANNEL_ID.toULong()),
    userId = UserId(USER_ID.toULong()),
    locale = guildLocale,
    guildLocale = guildLocale,
    stringOptions = options,
)

internal fun playerActor(
    guildId: GuildId = GuildId(GUILD_ID.toULong()),
    guildLocale: LocaleCode? = null,
): PlayerActor = PlayerActor(
    guildId = guildId,
    userId = UserId(USER_ID.toULong()),
    channelId = ChannelId(CHANNEL_ID.toULong()),
    guildLocale = guildLocale,
)

/** Track whose [PlayerTrack.displayName] is exactly [displayName], as Kord's queue renders it. */
internal fun testTrack(displayName: String): PlayerTrack = PlayerTrack(
    identifier = displayName,
    title = displayName,
    author = "Artist",
    customName = displayName,
)

internal fun queueSnapshot(
    guildName: String = "Guild",
    tracks: List<PlayerTrack> = emptyList(),
): PlayerQueueSnapshot = PlayerQueueSnapshot(
    guildName = guildName,
    snapshot = PlayerSnapshot(queue = tracks),
)

internal object NoPersistedLocale : LocaleRepository {

    override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

    override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
        error("command tests must not persist a guild locale")

    override suspend fun removeGuildLocale(guildId: GuildId) =
        error("command tests must not persist a guild locale")

    override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
}
