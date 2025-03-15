package es.wokis.commands

enum class ComponentsEnum(val customId: String) {
    QUEUE_PREVIOUS("queue_previous"),
    QUEUE_NEXT("queue_next"),
    PLAYER_RESUME("player_resume"),
    PLAYER_PAUSE("player_pause"),
    PLAYER_SKIP("player_skip"),
    PLAYER_DISCONNECT("player_disconnect"),
    PLAYER_SHUFFLE("player_shuffle"),
    SOUNDS_PREVIOUS("sounds_previous"),
    SOUNDS_NEXT("sounds_next");

    companion object {
        fun forCustomId(customId: String) = entries.find { it.customId == customId }
    }
}
