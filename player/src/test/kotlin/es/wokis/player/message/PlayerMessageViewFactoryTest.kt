package es.wokis.player.message

import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.message.ButtonStyle
import es.wokis.message.ComponentId
import es.wokis.message.EmbedFieldView
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerMessageViewFactoryTest {

    private val guildId = GuildId(42uL)
    private val catalog = LocalizationCatalog.load()

    @Test
    fun `Given a current track and a queue When creating the view Then match the Kord embed layout`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val snapshot = PlayerSnapshot(
            current = track(title = "Song", durationMillis = 3_723_000L),
            queue = listOf(
                track(title = "One", durationMillis = 60_000L),
                track(title = "Two", durationMillis = 120_000L),
                track(title = "Three", durationMillis = 180_000L),
                track(title = "Four", durationMillis = 240_000L),
                track(title = "Five", durationMillis = 300_000L),
            ),
            hasActiveTrack = true,
        )

        val view = factory.createPlayerView("Jungle", snapshot, guildId, LocaleCode("en-US"))
        val embed = requireNotNull(view.embed)

        assertEquals("Jungle Player", embed.title)
        assertEquals(PLAYER_BRAND_COLOR, embed.color)
        assertEquals(0x01B05B, PLAYER_BRAND_COLOR)
        assertNull(embed.description)
        assertNull(embed.footer)
        assertEquals(
            listOf(
                ComponentId.PLAYER_PAUSE,
                ComponentId.PLAYER_SKIP,
                ComponentId.PLAYER_SHUFFLE,
                ComponentId.PLAYER_RECONNECT,
                ComponentId.PLAYER_DISCONNECT,
            ),
            view.rows.single().buttons.map { it.componentId },
        )
        assertEquals(ButtonStyle.DANGER, view.rows.single().buttons.last().style)
        assertEquals(
            listOf("Pause", "Skip", "Shuffle", "Reconnect", "Disconnect"),
            view.rows.single().buttons.map { it.label },
        )
        assertEquals(
            listOf("Current track", "Track duration", "Server's queue"),
            embed.fields.map(EmbedFieldView::name),
        )
        assertEquals("**Song**\nArtist", embed.fields[0].value)
        assertEquals("01:02:03", embed.fields[1].value)
        assertEquals(
            "Artist - One (00:01:00)\n" +
                "Artist - Two (00:02:00)\n" +
                "Artist - Three (00:03:00)\n" +
                "and another 2 tracks\n" +
                "00:15:00 remaining",
            embed.fields[2].value,
        )
        assertTrue(
            embed.fields.none { it.name == "Playback position" },
            "the Kord player disables position updates",
        )
    }

    @Test
    fun `Given a custom name When creating the view Then render the bold custom name only`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val snapshot = PlayerSnapshot(
            current = track(customName = "Radio Show"),
            hasActiveTrack = true,
        )

        val view = factory.createPlayerView("Jungle", snapshot, guildId)
        val embed = requireNotNull(view.embed)

        assertEquals("**Radio Show**", embed.fields.first().value)
    }

    @Test
    fun `Given a favicon and artwork When creating the view Then the favicon wins and artwork is the fallback`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val both = PlayerSnapshot(
            current = track(
                customFavicon = "https://cdn.example/favicon.png",
                artworkUrl = "https://cdn.example/art.png",
            ),
            hasActiveTrack = true,
        )
        val artworkOnly = PlayerSnapshot(current = track(artworkUrl = "https://cdn.example/art.png"))

        val bothView = requireNotNull(factory.createPlayerView("Jungle", both).embed)
        val artworkView = requireNotNull(factory.createPlayerView("Jungle", artworkOnly).embed)

        assertEquals("https://cdn.example/favicon.png", bothView.thumbnailUrl)
        assertEquals("https://cdn.example/art.png", artworkView.thumbnailUrl)
    }

    @Test
    fun `Given a blank favicon When creating the view Then never render an empty thumbnail`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val snapshot = PlayerSnapshot(
            current = track(customFavicon = " ", artworkUrl = "https://cdn.example/art.png"),
            hasActiveTrack = true,
        )

        val embed = requireNotNull(factory.createPlayerView("Jungle", snapshot).embed)

        assertNull(embed.thumbnailUrl)
    }

    @Test
    fun `Given unknown stream and normal durations When creating the view Then render each duration variant`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val unknown = PlayerSnapshot(current = track(durationMillis = PlayerTrack.UNKNOWN_DURATION))
        val stream = PlayerSnapshot(
            current = track(durationMillis = PlayerTrack.UNKNOWN_DURATION, isStream = true),
        )
        val normal = PlayerSnapshot(current = track(durationMillis = 3_723_000L))

        val unknownValue = requireNotNull(factory.createPlayerView("Jungle", unknown).embed).fields[1].value
        val streamValue = requireNotNull(factory.createPlayerView("Jungle", stream).embed).fields[1].value
        val normalValue = requireNotNull(factory.createPlayerView("Jungle", normal).embed).fields[1].value

        assertEquals("Unknown", unknownValue)
        assertEquals("Stream", streamValue)
        assertEquals("01:02:03", normalValue)
    }

    @Test
    fun `Given a queue at the preview limit When creating the view Then omit the remaining count`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val snapshot = PlayerSnapshot(
            queue = (1..3).map { index -> track(title = "Track $index", durationMillis = 30_000L) },
        )

        val view = factory.createPlayerView("Jungle", snapshot)
        val queueValue = requireNotNull(view.embed).fields.single().value

        assertEquals(1, view.rows.size, "a queued track must expose the player buttons")
        assertEquals(
            "Artist - Track 1 (00:00:30)\n" +
                "Artist - Track 2 (00:00:30)\n" +
                "Artist - Track 3 (00:00:30)\n" +
                "00:01:30 remaining",
            queueValue,
        )
    }

    @Test
    fun `Given a paused or playing track When creating the view Then show exactly resume or pause`() = runTest {
        val factory = PlayerMessageViewFactory(service())

        val pausedButtons = factory.createPlayerView(
            "Jungle",
            PlayerSnapshot(current = track(), paused = true, hasActiveTrack = true),
        ).rows.single().buttons
        val playingButtons = factory.createPlayerView(
            "Jungle",
            PlayerSnapshot(current = track(), paused = false, hasActiveTrack = true),
        ).rows.single().buttons

        assertEquals(ComponentId.PLAYER_RESUME, pausedButtons.first().componentId)
        assertEquals("Resume", pausedButtons.first().label)
        assertEquals("▶️", pausedButtons.first().emoji)
        assertTrue(pausedButtons.none { it.componentId == ComponentId.PLAYER_PAUSE })
        assertEquals(ComponentId.PLAYER_PAUSE, playingButtons.first().componentId)
        assertEquals("Pause", playingButtons.first().label)
        assertEquals("⏸", playingButtons.first().emoji)
        assertTrue(playingButtons.none { it.componentId == ComponentId.PLAYER_RESUME })
    }

    @Test
    fun `Given an empty snapshot When creating the view Then use the empty queue description`() = runTest {
        val factory = PlayerMessageViewFactory(service())

        val view = factory.createPlayerView("Jungle", PlayerSnapshot(), guildId)
        val embed = requireNotNull(view.embed)

        assertEquals("Currently, there are no tracks queued", embed.description)
        assertTrue(embed.fields.isEmpty())
        assertTrue(view.rows.isEmpty(), "an empty player must not expose buttons")
        assertNull(embed.thumbnailUrl)
    }

    @Test
    fun `Given a Spanish guild default When creating the view Then every label resolves in Spanish`() = runTest {
        val factory = PlayerMessageViewFactory(service())
        val snapshot = PlayerSnapshot(
            current = track(durationMillis = 3_723_000L),
            hasActiveTrack = true,
        )

        val embed = requireNotNull(
            factory.createPlayerView("Jungle", snapshot, guildId, LocaleCode("es-ES")).embed,
        )

        assertEquals("Reproductor de Jungle", embed.title)
        assertEquals("Pista de audio actual", embed.fields[0].name)
        assertEquals("Duración de la pista de audio", embed.fields[1].name)
        assertEquals("01:02:03", embed.fields[1].value)
    }

    @Test
    fun `Given a persisted Spanish locale When creating the view Then it wins over the guild default`() = runTest {
        val factory = PlayerMessageViewFactory(service(persisted = LocaleCode("es-ES")))

        val embed = requireNotNull(
            factory.createPlayerView(
                "Jungle",
                PlayerSnapshot(),
                guildId,
                LocaleCode("en-US"),
            ).embed,
        )

        assertEquals("Reproductor de Jungle", embed.title)
        assertEquals("Actualmente, no hay nada en la cola", embed.description)
    }

    private fun service(persisted: LocaleCode? = null): GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(FixedLocaleRepository(persisted)),
        catalog = catalog,
    )

    private fun track(
        title: String = "Song",
        durationMillis: Long = PlayerTrack.UNKNOWN_DURATION,
        isStream: Boolean = false,
        artworkUrl: String? = null,
        customName: String? = null,
        customFavicon: String? = null,
    ): PlayerTrack = PlayerTrack(
        identifier = title,
        title = title,
        author = "Artist",
        durationMillis = durationMillis,
        isStream = isStream,
        artworkUrl = artworkUrl,
        customName = customName,
        customFavicon = customFavicon,
    )

    private class FixedLocaleRepository(
        private val persisted: LocaleCode?,
    ) : LocaleRepository {

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = persisted

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("the view factory must not persist a guild locale")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("the view factory must not persist a guild locale")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }
}
