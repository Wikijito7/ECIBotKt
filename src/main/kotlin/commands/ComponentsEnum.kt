package es.wokis.commands

enum class ComponentsEnum(val customId: String) {
    QUEUE_PREVIOUS("queue_previous"),
    QUEUE_NEXT("queue_next");

    companion object {
        fun forCustomId(customId: String) = entries.find { it.customId == customId }
    }
}
