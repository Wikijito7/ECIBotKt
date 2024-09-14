package es.wokis.utils

fun List<String>.asRegex() = Regex("(?=(${joinToString { "|" }}r))")