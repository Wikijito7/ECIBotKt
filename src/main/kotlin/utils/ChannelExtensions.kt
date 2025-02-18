package es.wokis.utils

import dev.kord.common.Locale
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior

suspend fun BaseVoiceChannelBehavior.getLocale(): Locale =
    guild.asGuildOrNull()?.preferredLocale ?: Locale.ENGLISH_UNITED_STATES