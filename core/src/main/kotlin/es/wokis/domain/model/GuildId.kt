package es.wokis.domain.model

/**
 * Platform-neutral guild id carrying the raw unsigned 64-bit Discord snowflake.
 *
 * Kord's `Snowflake` clamps to `0..Long.MAX_VALUE`, so only neutral persistence (e.g. a decimal
 * string) preserves the full range; a round-trip through Kord narrows values above `Long.MAX_VALUE`.
 */
@JvmInline
value class GuildId(val value: ULong) {

    override fun toString(): String = value.toString()

    companion object {
        /**
         * Builds a [GuildId] from its decimal string representation.
         */
        fun fromDecimal(value: String): GuildId = GuildId(value.toULong())
    }
}
