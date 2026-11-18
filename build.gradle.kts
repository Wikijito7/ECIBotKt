import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.io.IOException
import java.util.concurrent.Callable
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import org.w3c.dom.Document
import org.xml.sax.SAXException

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    jacoco
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.plugin.serialization)
    alias(libs.plugins.idea.ext)
    alias(libs.plugins.detekt)
}

group = "es.wokis"
version = "1.0-SNAPSHOT"

repositories {
    flatDir {
        dirs("libs")
    }
    // Keep mavenLocal for transitive dependencies of local JARs
    mavenLocal()
    maven(url = "https://maven.lavalink.dev/releases")
    maven(url = "https://maven.topi.wtf/releases")
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // Neutral domain/command boundary shared with the isolated JDA runtime.
    implementation(project(":core"))
    // Framework-independent shared JSON configuration used by the Kord and JDA runtimes.
    implementation(project(":configuration"))
    // Shared JSON locale persistence used by both the Kord and JDA runtimes.
    implementation(project(":locale-persistence"))
    // Shared real-player engine used by the Kord adapter and the isolated JDA runtime.
    implementation(project(":player"))

    // Kord - Local SNAPSHOT JARs (voice encryption branch)
    // TODO: Switch back to version catalog when Kord merges voice encryption to main (kord#1063)
    implementation(fileTree("libs") { include("kord-*.jar") })

    // Transitive dependencies required by local Kord JARs (flatDir doesn't resolve these)
    // TODO: Remove when Kord merges voice encryption to main (kord#1063)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kord.cache)
    implementation(libs.kord.cache.map)

    //    implementation(libs.kord.core)
    //    implementation(libs.kord.voice)
    //    implementation(libs.kord.core.voice)
    //    implementation(libs.kord.rest)
    //    implementation(libs.kord.gateway)
    //    implementation(libs.kord.common)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    // TODO: Remove when Kord merges voice encryption to main (Kord needs OkHttp engine)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization.kotlin.json)
    implementation(libs.ktor.client.client.resources)
    implementation(libs.ktor.client.client.auth)
    implementation(libs.ktor.client.mock)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)

    // Logger
    implementation(libs.slf4j.simple)

    // Serialization
    implementation(libs.kotlinx.serialization)

    // libdave-jvm for DAVE protocol
    implementation(libs.libdave.api)
    implementation(libs.libdave.impl.jni)
    runtimeOnly(libs.libdave.natives.darwin)
    runtimeOnly(libs.libdave.natives.linuxX64)
    runtimeOnly(libs.libdave.natives.linuxAarch64)
    runtimeOnly(libs.libdave.natives.winX64)

    // Lavaplayer
    implementation(libs.lavaplayer)
    implementation(libs.lavaplayer.youtube)
    implementation(libs.lavaplayer.lavasrc)

    // Tests
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.junit.parametrized)

    // Detekt formatting ruleset (ktlint-backed formatting rules)
    detektPlugins(libs.detekt.formatting)
}

/*
 * Detekt is applied to the root project and to every included module. The formatting ruleset is
 * published as a separate artifact, so each project adds it to its own `detektPlugins`
 * configuration. Build scripts (`*.kts`) are not part of Detekt's Kotlin source sets and are
 * therefore not scanned by this gate.
 */
val detektConfigFile = layout.projectDirectory.file("config/detekt/detekt.yml")

fun Project.configureDetekt(configFile: File) {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(configFile)
        // The gate reports findings and never rewrites sources, locally or in CI.
        autoCorrect = false
        ignoreFailures = false
        parallel = true
        source.setFrom("src/main/kotlin", "src/test/kotlin")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
        reports {
            html.required.set(true)
            md.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
        }
    }
}

configureDetekt(detektConfigFile.asFile)

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        configureDetekt(detektConfigFile.asFile)
    }
}

/*
 * Repository-wide Detekt budget.
 *
 * `build.maxIssues` in `config/detekt/detekt.yml` is applied by Detekt per Gradle project, so on its
 * own it cannot enforce the repository-wide finding ceiling. This task runs every project's `detekt`
 * task and fails when the combined number of findings in their XML reports exceeds the same budget.
 * Keep it as the blocking gate: `check`/`build` and the Detekt workflow depend on it.
 *
 * The scope is derived from the build instead of a hand-maintained module list: the root project plus
 * every subproject that applies the Detekt plugin, so a module joins the repository-wide budget as
 * soon as it is Detekt-enabled. Detekt writes a Checkstyle-style XML report, which is read with a
 * hardened standard DOM parser (secure processing on, DTDs and external entities disabled) rather
 * than text matching, so entity escaping and formatting cannot skew the count.
 */
val detektFindingBudget = 40

fun detektScopedProjects(): List<Project> = buildList {
    add(project)
    addAll(subprojects.filter { scopedProject -> scopedProject.plugins.hasPlugin("io.gitlab.arturbosch.detekt") })
}

fun Project.detektBudgetLabel(): String = if (this == rootProject) "root" else path

fun parseDetektReport(reportFile: File): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    return try {
        factory.newDocumentBuilder().parse(reportFile)
    } catch (exception: ParserConfigurationException) {
        throw GradleException(
            "Cannot configure the secure XML parser for Detekt report '${reportFile.path}': ${exception.message}",
            exception,
        )
    } catch (exception: SAXException) {
        throw GradleException(
            "Cannot parse Detekt report '${reportFile.path}': ${exception.message}. " +
                "Re-run `./gradlew detektBudget` to regenerate it.",
            exception,
        )
    } catch (exception: IOException) {
        throw GradleException(
            "Cannot read Detekt report '${reportFile.path}': ${exception.message}",
            exception,
        )
    }
}

val detektBudget by tasks.registering {
    group = "verification"
    description = "Fails when the combined Detekt findings of the root project and every Detekt-enabled subproject exceed $detektFindingBudget."
    // Resolved lazily: this root script is evaluated before subproject scripts apply the Detekt plugin.
    dependsOn(Callable { detektScopedProjects().map { scopedProject -> scopedProject.tasks.named("detekt") } })

    doLast {
        val reports = detektScopedProjects().associateWith { scopedProject ->
            scopedProject.layout.buildDirectory.file("reports/detekt/detekt.xml").get().asFile
        }
        val missingReports = reports.filterValues { report -> !report.isFile }
        if (missingReports.isNotEmpty()) {
            throw GradleException(
                "Missing Detekt XML report(s): " +
                    missingReports.entries.joinToString { (scopedProject, report) ->
                        "'${scopedProject.detektBudgetLabel()}' (${report.path})"
                    } +
                    ". Run the aggregate gate so every project's detekt task runs first: " +
                    "`./gradlew detektBudget`.",
            )
        }

        val findingsByProject = reports.mapValues { (_, report) ->
            parseDetektReport(report).getElementsByTagName("error").length
        }
        val findings = findingsByProject.values.sum()
        val breakdown = findingsByProject.entries.joinToString { (scopedProject, projectFindings) ->
            "${scopedProject.detektBudgetLabel()}=$projectFindings"
        }
        if (findings > detektFindingBudget) {
            throw GradleException(
                "Repository-wide Detekt budget exceeded: $findings findings (limit $detektFindingBudget). " +
                    "Per project: $breakdown. " +
                    "Fix the findings instead of raising the budget, adding a baseline or disabling rules.",
            )
        }
        logger.lifecycle(
            "Detekt repository-wide budget OK: $findings/$detektFindingBudget findings ($breakdown).",
        )
    }
}

tasks.named("check") {
    dependsOn(detektBudget)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
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
        val projectKey = System.getenv("SONAR_PROJECT_KEY")
        val organization = System.getenv("SONAR_ORGANIZATION")
        val exclusions = listOf(
            "**/*BO.kt",
            "**/*DTO.kt",
            "**/*Exception.kt",
            "src/main/kotlin/Main.kt",
            "*.kts",
            "**/di/*.kt",
            "src/main/kotlin/commands/player/PlayerCommons.kt" // This class needs a lot of mocking of Kord, which isn't really prepared for it
        )
        property("sonar.projectKey", projectKey)
        property("sonar.organization", organization)
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.exclusions", exclusions)
    }
}

tasks.named("sonar") {
    // Ensure each module produces its XML coverage report before analysis
    dependsOn(
        ":core:jacocoTestReport",
        ":configuration:jacocoTestReport",
        ":locale-persistence:jacocoTestReport",
        ":player:jacocoTestReport",
        ":commands:jacocoTestReport",
        ":jda-runtime:jacocoTestReport",
    )
}

kotlin {
    jvmToolchain(21)
}

tasks.register("generateLangClass") {
    description = "Generates LocalizationKeys content from lang.yml keys"
    group = "lang"
    doLast {
        val baseLangFile = file("core/src/main/resources/lang/lang.yml")
        val outputFile = file("src/main/kotlin/localization/LocalizationKeys.kt")

        val fileContent = buildString {
            appendLine("package es.wokis.localization")
            appendLine()
            appendLine("object LocalizationKeys {")
            baseLangFile.readLines().forEach { line ->
                if (line.trim().isNotEmpty()) {
                    val langKey = line.split(": ").first().trim().replace(" ", "_")
                    val staticLangName = langKey.uppercase()
                    appendLine("    const val $staticLangName = \"$langKey\"")
                }
            }
            appendLine("}")
        }

        outputFile.writeText(fileContent)
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateLangClass")
}

idea.project.settings {
    taskTriggers {
        afterSync(project.tasks.named("generateLangClass"))
    }
}

application {
    mainClass.set("es.wokis.MainKt")
}

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
