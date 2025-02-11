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
    maven (url = "https://jitpack.io")
}

dependencies {
    implementation(libs.kord.core)
    implementation(libs.kord.voice)
    implementation(libs.ktor.client.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.slf4j.simple)
    implementation(libs.kotlinx.serialization)
    implementation(libs.lavaplayer)

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
            "**/di/*.kt"
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
