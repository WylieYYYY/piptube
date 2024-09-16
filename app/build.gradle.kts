import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.11.0")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.9.0-RC")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0-M2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "22.0.1"
    modules("javafx.controls", "javafx.fxml", "javafx.media", "javafx.swing")
    setPlatform(properties["platform"] as String? ?: "linux")
}

application {
    mainClass = "io.gitlab.wylieyyyy.piptube.AppKt"
}

tasks.withType<ShadowJar>().configureEach {
    mergeServiceFiles()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Main-Class"] = "io.gitlab.wylieyyyy.piptube.AppKt"
    }
    configurations["runtimeClasspath"].forEach { from(zipTree(it)) }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
