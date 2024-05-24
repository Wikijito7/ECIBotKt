val kordVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project

plugins {
    kotlin("jvm") version "1.9.23"
    jacoco
    id("org.sonarqube") version "3.5.0.2730"
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

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
}

jacoco {
    toolVersion = "0.8.11"
    reportsDirectory = layout.buildDirectory.dir("customJacocoReportDir")
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

kotlin {
    jvmToolchain(17)
}
