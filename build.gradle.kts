import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    jacoco
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.plugin.serialization)
    alias(libs.plugins.idea.ext)
}

group = "es.wokis"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://maven.lavalink.dev/releases")
    maven(url = "https://maven.topi.wtf/releases")
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
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization.kotlin.json)
    implementation(libs.ktor.client.client.resources)
    implementation(libs.ktor.client.client.auth)
    implementation(libs.ktor.client.mock)

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
    testImplementation(libs.junit.parametrized)
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
            "src/main/kotlin/services/lavaplayer/GuildLavaPlayerService.kt", // TODO: Right now, we cannot test it because of how the class is made.,
            "src/main/kotlin/commands/player/PlayerCommons.kt" // This class needs a lot of mocking of Kord, which isn't really prepared for it
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

tasks.register("generateLangClass") {
    description = "Generates LocalizationKeys content from lang.yml keys"
    group = "lang"
    doLast {
        val baseLangFile = file("src/main/resources/lang/lang.yml")
        val outputFile = file("src/main/kotlin/localization/LocalizationKeys.kt")

        val fileContent = buildString {
            appendLine("package es.wokis.localization")
            appendLine()
            appendLine("object LocalizationKeys {")
            baseLangFile.readLines().forEach { line ->
                if (line.trim().isNotEmpty()) {
                    val langKey = line.split(": ").first().trim().replace(" ", "_")
                    val staticLangName = langKey.uppercase()
                    appendLine("    const val $staticLangName = \"$langKey\"")
                }
            }
            appendLine("}")
        }

        outputFile.writeText(fileContent)
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateLangClass")
}

idea.project.settings {
    taskTriggers {
        afterSync(project.tasks.named("generateLangClass"))
    }
}

application {
    mainClass.set("es.wokis.MainKt")
}

tasks {
    val fatJar = register<Jar>("fatJar") {
        // We need this for Gradle optimization to work
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources", "distTar", "distZip"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        } + sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar)
    }
}
