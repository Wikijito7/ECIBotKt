package es.wokis.domain.model

/**
 * Platform-neutral channel identifier owned by the domain.
 *
 * Mirrors the raw unsigned 64-bit Discord snowflake so the full [ULong] decimal range
 * can be carried through the command boundary without a gateway-library type.
 */
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
