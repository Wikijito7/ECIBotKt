plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.plugin.serialization)
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
    implementation(project(":configuration"))
    implementation(project(":locale-persistence"))
    implementation(project(":player"))
    implementation(project(":commands"))
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

// Run from the repository root so Kord and JDA share `./data`. A packaged artifact needs the same
// CWD, and the two runtimes must not run concurrently against the same mutable data/token.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Single runnable artifact, mirroring the root `fatJar` convention.
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
