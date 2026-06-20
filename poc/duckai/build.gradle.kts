plugins {
    kotlin("jvm") version "2.1.21"
    application
    kotlin("plugin.serialization") version "2.1.21"
}

group = "es.wokis.poc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.graalvm.polyglot:polyglot:24.1.1")
    implementation("org.graalvm.polyglot:js:24.1.1")
    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-cio:3.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.5.16")
}

application {
    mainClass.set("DuckAiPocKt")
}

kotlin {
    jvmToolchain(21)
}
