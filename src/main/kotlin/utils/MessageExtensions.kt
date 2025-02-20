package es.wokis.utils

import dev.kord.common.Locale
import dev.kord.core.entity.Message

suspend fun Message.getGuildLocale(): Locale =
    data.guildId.value?.let { kord.getGuildOrNull(it)?.preferredLocale }.orDefaultLocale()
