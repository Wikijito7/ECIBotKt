val kordVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project

plugins {
    kotlin("jvm") version "1.9.23"
    jacoco
    id("org.sonarqube") version "5.0.0.4638"
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
    testImplementation("junit:junit:$junitVersion")
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
}

sonar {
    properties {
        property("sonar.java.source", "1.8")
        property("sonar.java.coveragePlugin", "jacoco")
        property("sonar.jacoco.reportPaths", "build/reports/jacoco/test-results/jacocoTestReport.xml")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "wokis")
        property("sonar.projectKey", "Wikijito7_ECIBotKt")
    }
}

kotlin {
    jvmToolchain(17)
}
