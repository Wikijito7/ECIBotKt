plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    application
    jacoco
}

group = "es.wokis"
version = rootProject.version

repositories {
    // Transitive source providers pulled in by the shared :player engine.
    maven(url = "https://maven.lavalink.dev/releases")
    maven(url = "https://maven.topi.wtf/releases")
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":locale-persistence"))
    implementation(project(":player"))
    implementation(libs.kotlinx.serialization)
    implementation(libs.jda)
    implementation(libs.libdaveV2Api)
    implementation(libs.libdaveV2ImplJni)
    implementation(libs.libdaveV2AdapterJda)
    runtimeOnly(libs.libdaveV2NativesDarwin)
    runtimeOnly(libs.libdaveV2NativesLinuxX64)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.simple)

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

application {
    mainClass.set("es.wokis.jda.JdaBootstrapKt")
}
