plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.alexispace"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // Hytale Server API
    compileOnly("com.hypixel.hytale:Server:+")
}

tasks.jar {
    archiveBaseName.set("HytaleVehicles")
}

tasks.shadowJar {
    archiveBaseName.set("HytaleVehicles")
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
