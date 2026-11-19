package es.wokis.commands.sound

/** Gateway-neutral view of the runtime's local sound folder. */
interface SoundsCatalogPort {

    /** Names of the available sounds without extension, in folder order. */
    suspend fun soundNames(): List<String>

    /** True when a playable sound named exactly [name] exists. */
    suspend fun soundExists(name: String): Boolean
}
