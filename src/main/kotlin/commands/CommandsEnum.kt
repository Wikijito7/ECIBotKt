package es.wokis.commands

enum class CommandsEnum(val commandName: String) {
    TEST("test");

    companion object {
        fun forCommandName(commandName: String) = entries.find { it.commandName == commandName }
    }
}