package es.wokis.exceptions

import kotlinx.serialization.SerializationException

/**
 * Raised when the shared JSON configuration cannot be decoded with the strict schema.
 *
 * The message is built by the loader and only names the configuration file, the decoding phase and
 * the failure category. kotlinx-serialization error messages can embed a raw `JSON input:` excerpt
 * (and even the whole decoded object for an unknown key), which would leak the Discord token and
 * every provider secret into logs. For that reason the original exception is deliberately never
 * chained as a [cause]: its message and stack trace are the leak. The loader raises this type for
 * every [SerializationException] **and** for the plain [IllegalArgumentException]s the decoder can
 * throw for some schema problems, so no decoder-originated text escapes either path.
 *
 * The type still extends [SerializationException] so callers that already handle decoding failures
 * keep their existing behaviour, and existing explicit validation errors
 * (`EmptyDiscordTokenException`, `EmptyDeezerMasterDecryptionKeyException`) are unaffected.
 */
class ConfigDecodingException(message: String) : SerializationException(message)
