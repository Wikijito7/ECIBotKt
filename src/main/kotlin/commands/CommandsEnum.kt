package es.wokis.commands

enum class CommandsEnum(val commandName: String) {
    PLAY("play"),
    QUEUE("queue"),
    SKIP("skip"),
    SHUFFLE("shuffle"),
    TTS("tts"),
    PLAYER("player");

    companion object {
        fun forCommandName(commandName: String) = entries.find { it.commandName == commandName }
    }
}
