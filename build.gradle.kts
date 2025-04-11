plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "io.github.pashaoleynik97"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
}

val ktorVersion = "2.3.5"

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion") // Optional
    implementation("io.ktor:ktor-client-plugins:$ktorVersion")

    implementation("org.jsoup:jsoup:1.16.1")

    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("BotMainKt")
}