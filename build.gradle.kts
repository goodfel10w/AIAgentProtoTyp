plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-server-html-builder:2.3.0")

    implementation("io.github.cdimascio:java-dotenv:5.2.2")

    // Koog dependency
    implementation("ai.koog:koog-agents:0.4.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}