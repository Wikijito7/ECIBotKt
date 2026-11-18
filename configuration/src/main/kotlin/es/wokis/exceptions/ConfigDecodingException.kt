package es.wokis.exceptions

import kotlinx.serialization.SerializationException

/**
 * Strict decoding failure; the original decoder exception is never chained because its message can
 * embed raw JSON and leak secrets.
 */
class ConfigDecodingException(message: String) : SerializationException(message)
