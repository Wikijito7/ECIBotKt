package es.wokis.services.lavaplayer

import com.github.topi314.lavalyrics.AudioLyricsManager
import com.github.topi314.lavalyrics.LyricsManager
import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import es.wokis.utils.Log

class LyricsService {
    private val lyricsManager = LyricsManager()

    fun registerLyricsManager(manager: AudioLyricsManager) {
        lyricsManager.registerLyricsManager(manager)
    }

    fun loadLyrics(track: AudioTrack): AudioLyrics? = try {
        lyricsManager.loadLyrics(track)
    } catch (e: Exception) {
        Log.error("Failed to load lyrics", exception = e)
        null
    }

    fun getFormattedLyrics(track: AudioTrack): String? = loadLyrics(track)?.text
}
