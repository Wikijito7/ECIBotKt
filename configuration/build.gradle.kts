plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.plugin.serialization)
    alias(libs.plugins.detekt)
    jacoco
}

group = "es.wokis"
version = rootProject.version

repositories {
    mavenCentral()
}

/*
 * Framework-independent configuration module.
 *
 * It only uses kotlinx-serialization and the JDK: no Koin, Kord, JDA, Lavaplayer, `:core`,
 * `:player` or `:locale-persistence` dependency, so the same strict JSON schema can be loaded by
 * the root Kord runtime, the isolated JDA runtime and future composition roots. Keeping the module
 * free of gateways is what lets the configuration surface survive the Phase 6 cutover without a
 * second copy of the schema.
 */
dependencies {
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockk)
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
