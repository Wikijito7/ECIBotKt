val kordVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project

plugins {
    kotlin("jvm") version "1.9.23"
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
}

kotlin {
    jvmToolchain(17)
}