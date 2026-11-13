package es.wokis.domain.model

/**
 * Platform-neutral guild identifier owned by the domain.
 *
 * Wraps the raw unsigned 64-bit Discord snowflake so the full [ULong] decimal range
 * can be persisted without loss, independently of any gateway library.
 *
 * Note that Kord's `Snowflake` clamps its value to `0..Long.MAX_VALUE`, so only neutral
 * persistence (for example as a decimal string) preserves the full range; converting to
 * a Kord `Snowflake` and back narrows values above `Long.MAX_VALUE`.
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
