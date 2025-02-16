plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.plugin.serialization)
}

group = "es.wokis"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://maven.lavalink.dev/releases")
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // Kord
    implementation(libs.kord.core)
    implementation(libs.kord.voice)
    implementation(libs.kord.core.voice)
    implementation(libs.kord.rest)
    implementation(libs.kord.gateway)
    implementation(libs.kord.common)

    // Ktor
    implementation(libs.ktor.client.core)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)

    // Logger
    implementation(libs.slf4j.simple)

    // Serialization
    implementation(libs.kotlinx.serialization)

    // Lavaplayer
    implementation(libs.lavaplayer)
    implementation(libs.lavaplayer.youtube)
    implementation(libs.lavaplayer.lavasrc)

    // Tests
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
    reportsDirectory = layout.buildDirectory.dir("customJacocoReportDir")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required = true
        xml.outputLocation.set(file("build/reports/jacoco/test-results/jacocoTestReport.xml"))
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

sonar {
    properties {
        val projectKey = System.getenv("SONAR_PROJECT_KEY")
        val organization = System.getenv("SONAR_ORGANIZATION")
        val exclusions = listOf(
            "**/*BO.kt",
            "**/*DTO.kt",
            "**/*Exception.kt",
            "src/main/kotlin/Main.kt",
            "*.kts",
            "**/di/*.kt",
            "src/main/kotlin/services/lavaplayer/GuildLavaPlayerService.kt" // TODO: Right now, we cannot test it because of how the class is made.
        )
        property("sonar.projectKey", projectKey)
        property("sonar.organization", organization)
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.exclusions", exclusions)
    }
}

kotlin {
    jvmToolchain(17)
}
