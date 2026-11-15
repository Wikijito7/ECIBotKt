plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

group = "es.wokis"
version = rootProject.version

repositories {
    maven(url = "https://maven.lavalink.dev/releases")
    maven(url = "https://maven.topi.wtf/releases")
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lavaplayer)
    implementation(libs.lavaplayer.youtube)
    implementation(libs.lavaplayer.lavasrc)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required = true
    }
}

kotlin {
    jvmToolchain(21)
}
