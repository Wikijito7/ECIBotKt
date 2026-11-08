plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "es.wokis"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
