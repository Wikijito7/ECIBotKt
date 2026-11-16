plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
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
    // `Config.toPlayerSourcesConfig()` is part of this module's public API, so its receiver type
    // (`Config`, owned by `:configuration`) must be on the consumers' compile classpath rather than
    // only on ours. Publishing it as `api` keeps downstream modules from having to add `:configuration`
    // themselves just to call the mapping.
    api(project(":configuration"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lavaplayer)
    implementation(libs.lavaplayer.youtube)
    implementation(libs.lavaplayer.lavasrc)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.slf4j.simple)
    detektPlugins(libs.detekt.formatting)
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
