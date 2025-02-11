package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

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
        if (player.isPaused) {
            nextTrack()
        }
    }

    private fun nextTrack() {
        player.startTrack(queue.removeAt(0), true)
    }
}
