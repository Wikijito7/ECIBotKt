plugins {
    kotlin("jvm")
}

group = "spike.jdadave"
version = "0.1.0"

repositories {
    mavenCentral()
}

val jdaVersion = "6.7.0"
val jdaKtxVersion = "0.14.2"
val libdaveVersion = "0.2.0"

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("club.minnced:jda-ktx:$jdaKtxVersion")

    implementation("moe.kyokobot.libdave:api:$libdaveVersion")
    implementation("moe.kyokobot.libdave:impl-jni:$libdaveVersion")
    implementation("moe.kyokobot.libdave:adapter-jda:$libdaveVersion")
    runtimeOnly("moe.kyokobot.libdave:natives-linux-x86-64:$libdaveVersion")

    implementation("org.slf4j:slf4j-api:2.0.19")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.19")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.0")
    testImplementation("io.mockk:mockk:1.14.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

// jda-ktx 0.14.2 requests JDA 6.3.2 transitively. The spike deliberately tests the
// approved explicit 6.7.0 candidate instead. The force is scoped to the spike's own
// compile/runtime classpaths so it cannot leak into unrelated configurations
// (toolchain, plugin, or detached configurations).
val jdaForcedConfigurations = setOf(
    "compileClasspath",
    "runtimeClasspath",
    "testCompileClasspath",
    "testRuntimeClasspath",
)

configurations.configureEach {
    if (name in jdaForcedConfigurations) {
        resolutionStrategy {
            force("net.dv8tion:JDA:$jdaVersion")
        }
    }
}

/**
 * Self-contained staging probe. This is intentionally a spike-local task: the root
 * production build, version catalog and runtimeClasspath are not involved.
 *
 * The jar contains JDA, jda-ktx, the libdave modules, the Linux x86-64 DAVE native and
 * the opus/JNA natives needed by JDA's PCM encoder. Keep the native entries intact when
 * inspecting the artifact.
 */
val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the self-contained Linux x86-64 staging probe jar."

    archiveBaseName.set("jda-dave-spike")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "spike.jdadave.MainKt",
            "Implementation-Title" to "jda-dave-spike",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true",
        )
    }

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })

    exclude(
        "META-INF/MANIFEST.MF",
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "module-info.class",
        "META-INF/versions/*/module-info.class",
    )

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
