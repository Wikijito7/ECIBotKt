package es.wokis.commands

enum class ComponentsEnum(val customId: String) {
    QUEUE_PREVIOUS("queue_previous"),
    QUEUE_NEXT("queue_next"),
    PLAYER_SKIP("player_skip"),
    PLAYER_DISCONNECT("player_disconnect"),
    PLAYER_SHUFFLE("player_shuffle");

    companion object {
        fun forCustomId(customId: String) = entries.find { it.customId == customId }
    }
}
