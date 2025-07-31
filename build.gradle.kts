group = "ru.joutak"

// Версия с git hash
val baseVersion = System.getProperty("version") ?: "alpha-0.1"
val commitHash = System.getProperty("commitHash")
val computedVersion = if (!commitHash.isNullOrBlank()) "$baseVersion-$commitHash" else baseVersion
version = computedVersion

val targetJavaVersion = 21

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly("dev.folia:folia-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(21)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    archiveFileName.set("${project.name}.jar")

    val serverPath = System.getenv("SERVER_PATH")
    if (System.getenv("TESTING") != null) {
        if (serverPath != null) {
            destinationDirectory.set(file("$serverPath/plugins"))
        } else {
            logger.warn("SERVER_PATH property is not set!")
        }
    }
}

tasks.jar {
    finalizedBy("shadowJar")
    enabled = false
}

tasks.processResources {
    val props = mapOf("version" to version, "pluginName" to project.name)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}