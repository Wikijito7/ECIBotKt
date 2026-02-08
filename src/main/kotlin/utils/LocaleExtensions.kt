package es.wokis.utils

import dev.kord.common.Locale

fun Locale?.orDefaultLocale() = this ?: Locale.ENGLISH_UNITED_STATES
