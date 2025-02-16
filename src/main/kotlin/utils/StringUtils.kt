package es.wokis.utils

fun String.takeIfNotEmpty() = takeIf { it.isNotEmpty() }
