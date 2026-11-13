plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

group = "es.wokis"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
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
