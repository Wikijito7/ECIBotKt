plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "es.wokis"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.jda)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("es.wokis.jda.JdaBootstrapKt")
}
