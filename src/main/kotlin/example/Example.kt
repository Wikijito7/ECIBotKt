package es.wokis.example

data class Example(
    val name: String = ""
) {
    fun getCoolName(): String = "$name :sunglasses:"
}