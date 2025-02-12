package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import es.wokis.bot.Bot
import org.slf4j.LoggerFactory

class TrackScheduler(
    private val player: AudioPlayer,
) : AudioEventAdapter() {

    private val queue: MutableList<AudioTrack> = mutableListOf()

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        if (endReason?.mayStartNext == true) {
            nextTrack()
        }
    }

    fun queue(track: AudioTrack) {
        queue.add(track)
        if (player.playingTrack == null) {
            nextTrack()
        }
    }

    private fun nextTrack() {
        LoggerFactory.getLogger(Bot::class.java).info("Playing next track")
        player.startTrack(queue.removeAt(0), true)
    }
}
