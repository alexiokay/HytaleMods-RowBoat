plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

// Apply our custom run-hytale plugin
apply<RunHytalePlugin>()

group = "com.alexispace"
version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // Hytale Server API
    compileOnly("com.hypixel.hytale:Server:+")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.jar {
    archiveBaseName.set("AlexisRowboat")
}

tasks.shadowJar {
    archiveBaseName.set("AlexisRowboat")
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Configure the Hytale server runner
// It auto-detected your installation at F:\games\hytale
configure<RunHytaleExtension> {
    serverPath = "F:/games/hytale"
}

// CFR Decompiler configuration for reverse engineering
configurations {
    create("cfr")
}

dependencies {
    "cfr"("org.benf:cfr:0.152")
}

// Task to decompile Hytale Server JAR and search for classes
tasks.register("decompileServer") {
    group = "hytale"
    description = "Decompile Hytale Server JAR to find API classes"

    doLast {
        val hytaleDir = file("F:/games/hytale")
        val serverJar = fileTree(hytaleDir) {
            include("**/Server*.jar", "**/hytale-server*.jar", "**/server.jar")
        }.files.firstOrNull()

        if (serverJar == null) {
            // Try gradle cache
            val gradleCache = file(System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale")
            if (gradleCache.exists()) {
                val cachedJar = fileTree(gradleCache) {
                    include("**/Server*.jar")
                }.files.firstOrNull()
                if (cachedJar != null) {
                    println("Found Server JAR in Gradle cache: ${cachedJar.absolutePath}")
                    decompileJar(cachedJar)
                    return@doLast
                }
            }
            println("ERROR: Could not find Hytale Server JAR")
            println("Searched in: $hytaleDir")
            return@doLast
        }

        println("Found Server JAR: ${serverJar.absolutePath}")
        decompileJar(serverJar)
    }
}

fun decompileJar(jarFile: File) {
    val outputDir = file("${project.buildDir}/decompiled")
    outputDir.mkdirs()

    println("Decompiling to: ${outputDir.absolutePath}")
    println("This may take a few minutes...")

    // Run CFR decompiler
    javaexec {
        classpath = configurations["cfr"]
        mainClass.set("org.benf.cfr.reader.Main")
        args = listOf(
            jarFile.absolutePath,
            "--outputdir", outputDir.absolutePath,
            "--silent", "true"
        )
    }

    println("\nDecompilation complete!")
    println("Output: ${outputDir.absolutePath}")
}

// Task to search for specific classes in decompiled output
tasks.register("searchClass") {
    group = "hytale"
    description = "Search for a class in decompiled output. Usage: ./gradlew searchClass -Pquery=Interaction"

    doLast {
        val query = project.findProperty("query")?.toString() ?: "Interaction"
        val outputDir = file("${project.buildDir}/decompiled")

        if (!outputDir.exists()) {
            println("Run './gradlew decompileServer' first!")
            return@doLast
        }

        println("Searching for '$query' in decompiled sources...")
        println("=" .repeat(60))

        fileTree(outputDir) {
            include("**/*.java")
        }.files.filter { file ->
            file.name.contains(query, ignoreCase = true)
        }.sortedBy { it.name }.take(50).forEach { file ->
            val relativePath = file.relativeTo(outputDir).path.replace("\\", "/").replace(".java", "")
            val packagePath = relativePath.replace("/", ".")
            println("import $packagePath;")
        }
    }
}
