package es.wokis.domain.model

/** Platform-neutral user id carrying the raw unsigned 64-bit Discord snowflake. */
@JvmInline
value class UserId(val value: ULong) {

    override fun toString(): String = value.toString()

    companion object {
        /**
         * Builds a [UserId] from its decimal string representation.
         */
        fun fromDecimal(value: String): UserId = UserId(value.toULong())
    }
}
