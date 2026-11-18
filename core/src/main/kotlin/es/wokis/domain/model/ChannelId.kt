package es.wokis.domain.model

/** Platform-neutral channel id carrying the raw unsigned 64-bit Discord snowflake. */
@JvmInline
value class ChannelId(val value: ULong) {

    override fun toString(): String = value.toString()

    companion object {
        /**
         * Builds a [ChannelId] from its decimal string representation.
         */
        fun fromDecimal(value: String): ChannelId = ChannelId(value.toULong())
    }
}
