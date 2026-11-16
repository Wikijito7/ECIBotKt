package es.wokis.exceptions

/**
 * Raised when the shared JSON configuration does not satisfy the required semantics.
 *
 * These types are intentionally free of gateway/build dependencies: both the root Kord runtime
 * and the isolated JDA runtime fail fast on the same invalid values.
 */
class EmptyDiscordTokenException : IllegalArgumentException("Discord token shouldn't be empty")

class EmptyDeezerMasterDecryptionKeyException : IllegalArgumentException(
    "Deezer is enabled but no master decryption key was provided"
)
