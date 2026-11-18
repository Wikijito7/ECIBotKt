@file:OptIn(KordVoice::class)

package es.wokis.services.lavaplayer

import dev.kord.common.Locale
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import es.wokis.commands.player.createPlayerEmbed
import es.wokis.dispatchers.AppDispatchers
import es.wokis.localization.LocalizationKeys
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import es.wokis.utils.createCoroutineScope
import es.wokis.utils.getLocale
import es.wokis.utils.isValidUrl
import es.wokis.utils.toSanitizedMarkdownLink
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.schedule

private const val TAG = "GuildLavaPlayerService"

private const val LEAVE_DELAY = 10000L
private const val UNKNOWN_ERROR = "Unknown error"
private const val RECONNECT_DELAY = 500L

/**
 * Kord-side adapter over the shared per-guild player engine.
 *
 * Voice connection, player-channel messages/embeds, the leave timer, reconnect and locale
 * presentation are Kord gateway concerns and stay here; queue, track, pause/resume and retry state
 * live in [PlayerEngine]. The adapter maps engine events to the existing Kord messages and maps
 * the continuous engine audio stream to Kord [AudioFrame]s.
 */
class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior,
    private val playerEngine: PlayerEngine,
    private val localizationService: LocalizationService,
    val guildId: Snowflake,
    var discordLocale: Locale? = null
) {

    @Volatile
    private var voiceConnection: VoiceConnection? = null

    @Volatile
    private var audioStream: ContinuousPlayerAudioStream? = null
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)
    private val voiceConnectionMutex = Mutex()
    private val voiceGeneration = AtomicLong(0)
    private val connectingToVoiceChannel = AtomicBoolean(false)
    private var leaveTimer: Timer? = null
    private val isReconnecting = AtomicBoolean(false)
    private var playerMessage: Message? = null
    private val updateSeekChannel = Channel<Unit>(Channel.CONFLATED)

    init {
        coroutineScope.launch {
            playerEngine.events.collect(::onPlayerEvent)
        }
        coroutineScope.launch {
            for (event in updateSeekChannel) {
                updatePlayerEmbed()
            }
        }
    }

    fun loadAndPlay(url: String, addToFront: Boolean = false) {
        coroutineScope.launch {
            playerEngine.loadAndPlay(url, addToFront)
        }
    }

    fun loadAndPlayMultiple(tracks: List<String>) {
        coroutineScope.launch {
            playerEngine.loadAndPlayMultiple(tracks)
        }
    }

    fun loadAndPlayMultipleWithCustomName(tracks: List<String>, customName: String) {
        coroutineScope.launch {
            playerEngine.loadAndPlayMultipleWithCustomName(tracks, customName)
        }
    }

    suspend fun loadAndPlayTts(message: String) {
        playerEngine.loadAndPlayTts(message)
    }

    suspend fun playRadio(radioName: String, radioUrl: String, customFavicon: String) {
        playerEngine.playRadio(radioName, radioUrl, customFavicon)
    }

    fun getQueue(): List<TrackBO> = playerEngine.snapshot().queue

    fun getCurrentPlayingTrack(): TrackBO? = playerEngine.snapshot().current

    fun isPaused(): Boolean = playerEngine.isPaused()

    suspend fun resume() {
        playerEngine.resume()
        updateSeekChannel.trySend(Unit)
    }

    suspend fun pause() {
        playerEngine.pause()
        updateSeekChannel.trySend(Unit)
    }

    suspend fun skip() {
        playerEngine.skip()
        updateSeekChannel.trySend(Unit)
    }

    suspend fun moveTrackToNext(searchTerm: String): TrackBO? = playerEngine.moveTrackToNext(searchTerm)

    fun isQueueEmpty(): Boolean = playerEngine.isQueueEmpty()

    suspend fun stop() {
        handleDisconnectEvent()
    }

    suspend fun shuffle() {
        playerEngine.shuffle()
        updateSeekChannel.trySend(Unit)
    }

    suspend fun shutdown() {
        leaveTimer?.cancel()
        leaveTimer = null
        try {
            resetVoiceConnection()
        } finally {
            playerEngine.close()
            coroutineScope.cancel()
        }
    }

    suspend fun handleDisconnectEvent() {
        if (isReconnecting.get()) return
        playerEngine.stop()
        resetVoiceConnection()
        resetLeaveTimer()
        updateSeekChannel.trySend(Unit)
    }

    fun savePlayerMessage(message: Message) {
        this.playerMessage = message
    }

    fun isConnected(): Boolean = voiceConnection != null

    suspend fun reconnect() {
        if (isReconnecting.get() || !isConnected()) return
        if (!isReconnecting.compareAndSet(false, true)) return
        try {
            resetVoiceConnection()
            kotlinx.coroutines.delay(RECONNECT_DELAY)
            connectToVoiceChannel()
        } finally {
            isReconnecting.set(false)
        }
    }

    private suspend fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.TrackQueued -> onTrackQueued(event)

            is PlayerEvent.PlaylistQueued -> onPlaylistQueued(event)

            is PlayerEvent.NoMatches -> onNoMatches(event.input)

            is PlayerEvent.LoadFailed -> onLoadFailed(event.message)

            is PlayerEvent.PlaybackFailed -> onLoadFailed(event.message)

            is PlayerEvent.TrackStarted -> onTrackStarted()

            is PlayerEvent.TrackEnded -> {
                if (event.queueEmpty) setUpTimer()
                updateSeekChannel.trySend(Unit)
            }

            is PlayerEvent.QueueChanged -> updateSeekChannel.trySend(Unit)

            PlayerEvent.Stopped -> updateSeekChannel.trySend(Unit)
        }
    }

    private suspend fun onTrackQueued(event: PlayerEvent.TrackQueued) {
        resetLeaveTimer()
        discordLocale = voiceChannel.getLocale()
        val displayName = event.track.displayName
        val uri = event.uri
        val content = if (uri?.isValidUrl() == true) {
            localizationService.getStringFormat(
                key = if (event.addToFront) {
                    LocalizationKeys.NEXT_ADDED_TO_QUEUE_WITH_LINK
                } else {
                    LocalizationKeys.ADDED_TRACK_TO_QUEUE_WITH_LINK
                },
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(displayName.toSanitizedMarkdownLink(uri))
            )
        } else {
            localizationService.getStringFormat(
                key = if (event.addToFront) LocalizationKeys.NEXT_ADDED_TO_QUEUE else LocalizationKeys.ADDED_TRACK_TO_QUEUE,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(displayName)
            )
        }
        textChannel.createMessage(content)
        connectToVoiceChannel()
    }

    private suspend fun onPlaylistQueued(event: PlayerEvent.PlaylistQueued) {
        resetLeaveTimer()
        discordLocale = voiceChannel.getLocale()
        val message = textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.FOUND_TRACK_LIST,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf<Any>(event.name, event.trackCount)
            )
        )
        connectToVoiceChannel()
        val playlistUrl = event.playlistUrl
        message.edit {
            content = if (playlistUrl?.isValidUrl() == true) {
                localizationService.getStringFormat(
                    key = if (event.addToFront) {
                        LocalizationKeys.NEXT_ADDED_SONGS_TO_QUEUE_WITH_LINK
                    } else {
                        LocalizationKeys.ADDED_SONGS_TO_QUEUE_WITH_LINK
                    },
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf<Any>(
                        event.name.toSanitizedMarkdownLink(playlistUrl),
                        event.trackCount
                    )
                )
            } else {
                localizationService.getStringFormat(
                    key = if (event.addToFront) LocalizationKeys.NEXT_ADDED_SONGS_TO_QUEUE else LocalizationKeys.ADDED_SONGS_TO_QUEUE,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf<Any>(event.name, event.trackCount)
                )
            }
        }
    }

    private suspend fun onNoMatches(input: String) {
        discordLocale = voiceChannel.getLocale()
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.NO_MATCHES,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(input)
            )
        )
    }

    private suspend fun onLoadFailed(message: String?) {
        Log.error("GuildLavaPlayerService onLoadFailed: ${message ?: UNKNOWN_ERROR}")
        discordLocale = voiceChannel.getLocale()
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.LOAD_FAILED,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(message ?: UNKNOWN_ERROR)
            )
        )
    }

    private suspend fun onTrackStarted() {
        discordLocale = voiceChannel.getLocale()
        val voiceChannelName = voiceChannel.asChannel().name
        if (playerMessage != null) {
            updateSeekChannel.trySend(Unit)
        } else {
            sendNowPlayingMessage(discordLocale, voiceChannelName)
        }
    }

    private suspend fun sendNowPlayingMessage(
        discordLocale: Locale?,
        voiceChannelName: String
    ) {
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.NOW_PLAYING,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(playerEngine.snapshot().current?.displayName.orEmpty(), voiceChannelName)
            )
        )
    }

    private fun setUpTimer() {
        // Cancel any pending leave first so repeated TrackEnded events cannot leave racing timers.
        resetLeaveTimer()
        leaveTimer = Timer(true).apply {
            schedule(LEAVE_DELAY) {
                if (!playerEngine.isQueueEmpty()) return@schedule
                coroutineScope.launch {
                    resetVoiceConnection()
                }
            }
        }
    }

    private fun resetLeaveTimer() {
        leaveTimer?.cancel()
        leaveTimer = null
    }

    private suspend fun connectToVoiceChannel() {
        if (voiceConnection != null) return
        if (!connectingToVoiceChannel.compareAndSet(false, true)) return
        val generation = voiceGeneration.get()

        try {
            voiceConnectionMutex.withLock {
                // A disconnect/reset that happened while this connect was waiting must win.
                if (voiceConnection != null || generation != voiceGeneration.get()) return@withLock
                val stream = playerEngine.audioStream()
                audioStream = stream
                try {
                    voiceConnection = voiceChannel.connect {
                        audioProvider {
                            when (val next = stream.next()) {
                                is PlayerAudioState.Frame -> AudioFrame.fromData(next.frame.payloadCopy())
                                PlayerAudioState.PausedSilence -> AudioFrame.SILENCE
                                PlayerAudioState.Ended -> null
                            }
                        }
                        selfDeaf = true
                    }
                } catch (error: Throwable) {
                    stream.close()
                    audioStream = null
                    throw error
                }
            }
        } finally {
            connectingToVoiceChannel.set(false)
        }
    }

    private suspend fun resetVoiceConnection() {
        voiceGeneration.incrementAndGet()
        voiceConnectionMutex.withLock {
            audioStream?.close()
            audioStream = null
            voiceConnection?.leave()
            voiceConnection = null
        }
    }

    private suspend fun updatePlayerEmbed() {
        playerMessage?.let {
            val guildName = textChannel.data.guildId.value?.let { guildId ->
                textChannel.kord.getGuild(
                    guildId
                )
            }?.name.orEmpty()
            val snapshot = playerEngine.snapshot()
            try {
                it.edit {
                    createPlayerEmbed(
                        guildId = guildId,
                        discordLocale = discordLocale,
                        guildName = guildName,
                        localizationService = localizationService,
                        currentTrack = snapshot.current,
                        queue = snapshot.queue,
                        isPaused = snapshot.paused
                    )
                }
            } catch (t: Throwable) {
                Log.error("There's been an error trying to update the embed message on $guildName", t)
            }
        }
    }
}
