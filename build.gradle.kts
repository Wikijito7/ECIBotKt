val kordVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project

plugins {
    kotlin("jvm") version "1.9.23"
    jacoco
    id("org.sonarqube") version "4.4.1.3373"
}

group = "es.wokis"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("dev.kord:kord-voice:$kordVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.slf4j:slf4j-simple:$slf4jVersion")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

jacoco {
    toolVersion = "0.8.11"
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
    finalizedBy(tasks.sonar)
}

sonar {
    properties {
        property("sonar.projectKey", "Wikijito7_ECIBotKt")
        property("sonar.organization", "wokis")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

kotlin {
    jvmToolchain(17)
}
