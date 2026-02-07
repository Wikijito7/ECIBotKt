package es.wokis.commands

sealed class CommandName(val commandName: String) {
    data object Play : CommandName("play")
    data object Sound : CommandName("sound")
    data object Queue : CommandName("queue")
    data object Skip : CommandName("skip")
    data object Shuffle : CommandName("shuffle")
    data object Tts : CommandName("tts")
    data object Player : CommandName("player")
    data object Sounds : CommandName("sounds")
    data object Reconnect : CommandName("reconnect")
    data object Next : CommandName("next")
    data object Disconnect : CommandName("disconnect")
    data object Radio : CommandName("radio") {
        data object Play : CommandName("play")
        data object List : CommandName("list")
        data object Search : CommandName("search") {
            data object Name : CommandName("name")
            data object CountryCode : CommandName("countrycode")
        }
        data object Random : CommandName("random")
        data object CountryCodes : CommandName("countrycodes")
    }
}
