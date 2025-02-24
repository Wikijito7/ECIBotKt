package es.wokis.utils

fun String.takeIfNotEmpty() = takeIf { it.isNotEmpty() }

fun String.isValidUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

