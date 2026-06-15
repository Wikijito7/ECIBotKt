package es.wokis.commands.config

val CONFIG_VALID_SECTIONS = mapOf(
    "database" to listOf("enabled", "username", "password", "database"),
    "youtube" to listOf("enabled", "oauth2_token", "po_token", "visitor_data", "remote_cipher_url", "remote_cipher_password"),
    "deezer" to listOf("enabled", "master_decryption_key", "arl_token"),
    "spotify" to listOf("enabled", "client_id", "client_secret", "custom_endpoint"),
    "tidal" to listOf("enabled", "country_code", "token"),
    "kokoro" to listOf("enabled", "base_url", "default_voice", "default_speed", "default_lang_code")
)

val CONFIG_SENSITIVE_KEYS = listOf("password", "token", "secret", "key")

fun isSensitiveKey(section: String, key: String): Boolean =
    CONFIG_SENSITIVE_KEYS.any { key.lowercase().contains(it) }
