plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(libs.konsist)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    detektPlugins(libs.detekt.rules.ktlint.wrapper)
}

detekt {
    buildUponDefaultConfig = false
}

application {
    mainClass.set("de.darkspirit510.episodeoverview.EpisodeOverviewKt")
}

// Include application.conf in the distribution
distributions {
    main {
        contents {
            from("src/main/resources") {
                include("application.conf")
                into("conf")
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

val installGitHooks by tasks.registering(Exec::class) {
    description = "Points git at .githooks so the pre-commit checks run"
    onlyIf { file(".git").exists() }
    commandLine("git", "config", "core.hooksPath", ".githooks")
}

tasks.compileKotlin {
    dependsOn(installGitHooks)
}
