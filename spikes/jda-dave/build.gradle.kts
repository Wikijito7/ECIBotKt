import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipFile

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
    "negativeTestRuntimeClasspath",
)

configurations.configureEach {
    if (name in jdaForcedConfigurations) {
        resolutionStrategy {
            force("net.dv8tion:JDA:$jdaVersion")
        }
    }
}

/**
 * Negative-test classpath: identical to [runtimeClasspath] except the Linux x86-64
 * libdave native module. Only that module is excluded, so the libdave Java classes,
 * JDA and the Opus/JNA natives stay present. Used exclusively by
 * [fatJarWithoutDaveNative]; it never affects the normal fat jar.
 */
val negativeTestRuntimeClasspath = configurations.create("negativeTestRuntimeClasspath") {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.runtimeClasspath.get())
    exclude(group = "moe.kyokobot.libdave", module = "natives-linux-x86-64")
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

/**
 * Negative-test staging jar. It uses the same packaging recipe and matching manifest
 * attributes as [fatJar], but declares its own `manifest` block; it does not reuse a
 * manifest object or file from [fatJar].
 *
 * The only difference is that [negativeTestRuntimeClasspath] omits
 * `moe.kyokobot.libdave:natives-linux-x86-64:0.2.0`. The libdave Java classes, JDA, the
 * Opus native and the JNA dispatch native remain, so startup must reach the DAVE native
 * probe and fail there before any gateway login.
 *
 * This task deliberately writes a different filename; the normal staging jar is untouched.
 */
val fatJarWithoutDaveNative by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the probe jar without the Linux x86-64 DAVE native (fail-fast negative test)."

    archiveBaseName.set("jda-dave-spike-negative-no-dave-native")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Must match fatJar so the negative run exercises the same entry point.
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
        negativeTestRuntimeClasspath.files.map { file ->
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

/**
 * Packaging guard for both jars. The task itself makes no Discord connection and performs no
 * login; it only inspects jar contents. Dependency resolution may still need network access
 * unless the required Maven dependencies and plugins are already in the local Gradle cache,
 * so this task must not be described as "offline".
 *
 * It verifies the negative jar lost exactly the DAVE native and nothing else:
 *
 * - the normal jar contains the DAVE native and the negative jar does not;
 * - both jars keep the Opus/JNA natives, the probe entry point, the libdave factory and a
 *   known JDA class;
 * - every entry shared by both jars has identical bytes (SHA-256), so the exclusion cannot
 *   have changed a class or resource;
 * - both jars declare the same `Main-Class`.
 *
 * The jars are built with reproducible timestamps and file order, so a clean rebuild yields
 * the same comparison result.
 */
val verifyNegativeTestJar by tasks.registering {
    group = "verification"
    description = "Checks the negative-test jar omits only the DAVE native while the normal jar keeps it."

    dependsOn(fatJar, fatJarWithoutDaveNative)

    val normalJar = fatJar.flatMap { it.archiveFile }
    val negativeJar = fatJarWithoutDaveNative.flatMap { it.archiveFile }
    inputs.files(normalJar, negativeJar)

    doLast {
        val normal = normalJar.get().asFile
        val negative = negativeJar.get().asFile

        check(normal.absolutePath != negative.absolutePath) {
            "Normal and negative jars must have different paths but both are $normal"
        }

        fun fileEntries(zip: ZipFile): Set<String> =
            zip.entries().asSequence()
                .map { it.name }
                .filterNot { it.endsWith("/") }
                .toSet()

        fun sha256(zip: ZipFile, entryName: String): String {
            val entry = zip.getEntry(entryName)
                ?: error("Entry $entryName disappeared while comparing the jars")
            val digest = MessageDigest.getInstance("SHA-256")
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }

        fun mainClass(file: File): String? =
            JarFile(file).use { it.manifest?.mainAttributes?.getValue("Main-Class") }

        val daveNative = "natives/linux-x86-64/libdave-jvm.so"
        val opusNative = "natives/linux-x86-64/libopus.so"
        val jnaNative = "com/sun/jna/linux-x86-64/libjnidispatch.so"
        val mainClassEntry = "spike/jdadave/MainKt.class"
        val daveApiEntry = "moe/kyokobot/libdave/NativeDaveFactory.class"
        // A JDA class that must survive the exclusion untouched.
        val jdaEntry = "net/dv8tion/jda/api/JDA.class"

        ZipFile(normal).use { normalZip ->
            ZipFile(negative).use { negativeZip ->
                val normalEntries = fileEntries(normalZip)
                val negativeEntries = fileEntries(negativeZip)

                check(daveNative in normalEntries) {
                    "Normal jar ${normal.name} is missing $daveNative; packaging is broken"
                }
                check(daveNative !in negativeEntries) {
                    "Negative jar ${negative.name} still contains $daveNative; the exclusion did not apply"
                }

                listOf(opusNative, jnaNative, mainClassEntry, daveApiEntry, jdaEntry).forEach { entry ->
                    check(entry in normalEntries) { "Normal jar ${normal.name} is missing $entry" }
                    check(entry in negativeEntries) { "Negative jar ${negative.name} is missing $entry" }
                }

                val onlyInNormal = normalEntries - negativeEntries
                val onlyInNegative = negativeEntries - normalEntries
                check(onlyInNormal == setOf(daveNative)) {
                    "Negative jar must differ from the normal jar only by $daveNative, " +
                        "but only-in-normal=$onlyInNormal"
                }
                check(onlyInNegative.isEmpty()) {
                    "Negative jar has entries absent from the normal jar: $onlyInNegative"
                }

                // Every remaining entry must be byte-identical: the exclusion may not alter
                // any class or resource. Compare sorted names so the first mismatch is stable.
                val sharedEntries = (normalEntries intersect negativeEntries).sorted()
                val differing = sharedEntries.filter { entry ->
                    sha256(normalZip, entry) != sha256(negativeZip, entry)
                }
                check(differing.isEmpty()) {
                    "Shared entries differ between the normal and negative jars " +
                        "(${differing.size} of ${sharedEntries.size}), first: " +
                        differing.take(10).joinToString()
                }

                val normalMain = mainClass(normal)
                val negativeMain = mainClass(negative)
                check(normalMain == "spike.jdadave.MainKt") {
                    "Normal jar Main-Class is $normalMain, expected spike.jdadave.MainKt"
                }
                check(negativeMain == normalMain) {
                    "Negative jar Main-Class ($negativeMain) does not match the normal jar ($normalMain)"
                }

                logger.lifecycle("verifyNegativeTestJar: OK")
                logger.lifecycle(
                    "  normal   : {} ({} entries, has {})",
                    normal.absolutePath,
                    normalEntries.size,
                    daveNative,
                )
                logger.lifecycle(
                    "  negative : {} ({} entries, no {})",
                    negative.absolutePath,
                    negativeEntries.size,
                    daveNative,
                )
                logger.lifecycle(
                    "  kept     : {}, {}, {}, {}, {}",
                    opusNative,
                    jnaNative,
                    mainClassEntry,
                    daveApiEntry,
                    jdaEntry,
                )
                logger.lifecycle("  shared   : {} entries byte-identical (SHA-256)", sharedEntries.size)
            }
        }
    }
}
