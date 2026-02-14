import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import java.io.File
import java.net.URL
import java.util.jar.JarFile

/**
 * Gradle plugin for running a local Hytale server for testing.
 * 
 * Usage: ./gradlew runServer
 * 
 * This will:
 * 1. Download the Hytale server if not present (or use local install)
 * 2. Build and shadow your plugin
 * 3. Copy plugin to run/mods/
 * 4. Start the server with interactive console
 */
open class RunHytaleExtension {
    var serverPath: String = ""  // Path to existing Hytale installation
    var serverJar: String = "run/hytale-server.jar"
    var runDir: String = "run"
    var modsDir: String = "run/mods"
    var jvmArgs: List<String> = listOf("-Xmx4G", "-Xms1G", "--enable-native-access=ALL-UNNAMED")
}

class RunHytalePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<RunHytaleExtension>("runHytale")
        
        project.afterEvaluate {
            // Try to find Hytale installation automatically
            val possiblePaths = listOf(
                "F:/games/hytale",
                System.getenv("LOCALAPPDATA")?.let { "$it/Hytale" },
                System.getenv("APPDATA")?.let { "$it/Hytale" },
                System.getProperty("user.home")?.let { "$it/Hytale" }
            ).filterNotNull()
            
            val hytalePath = possiblePaths.firstOrNull { File(it).exists() }
            if (hytalePath != null && extension.serverPath.isEmpty()) {
                extension.serverPath = hytalePath
                println("Found Hytale installation at: $hytalePath")
            }
            
            project.tasks.register<JavaExec>("runServer") {
                group = "hytale"
                description = "Run a local Hytale server for testing"
                
                dependsOn("shadowJar")
                
                doFirst {
                    val runDir = File(project.projectDir, extension.runDir)
                    val modsDir = File(project.projectDir, extension.modsDir)
                    
                    // Create directories
                    runDir.mkdirs()
                    modsDir.mkdirs()
                    
                    // Find the server JAR
                    val serverJar = findServerJar(project, extension)
                    if (serverJar == null || !serverJar.exists()) {
                        throw org.gradle.api.GradleException("""
                            |
                            |Hytale server not found!
                            |
                            |Options:
                            |1. Set your Hytale path in build.gradle.kts:
                            |   runHytale {
                            |       serverPath = "F:/games/hytale"
                            |   }
                            |
                            |2. Or download the server from https://hytale.com/server
                            |   and place it in: ${extension.runDir}/hytale-server.jar
                            |
                        """.trimMargin())
                    }
                    
                    // Copy plugin JAR to mods folder
                    val pluginJar = project.tasks.getByName("shadowJar").outputs.files.singleFile
                    val destJar = File(modsDir, pluginJar.name)
                    pluginJar.copyTo(destJar, overwrite = true)
                    println("Deployed plugin: ${destJar.absolutePath}")
                    
                    // Set classpath and main class
                    classpath = project.files(serverJar)
                    mainClass.set("com.hypixel.hytale.server.Main")
                    workingDir = runDir
                    jvmArgs = extension.jvmArgs
                    standardInput = System.`in`
                }
            }
            
            // Task to just deploy the plugin without running
            project.tasks.register<org.gradle.api.tasks.Copy>("deployPlugin") {
                group = "hytale"
                description = "Deploy plugin to run/mods folder (isolated testing)"
                
                dependsOn("shadowJar")
                
                from(project.tasks.getByName("shadowJar").outputs.files)
                into(File(project.projectDir, extension.modsDir))
                
                doLast {
                    println("Plugin deployed to ${extension.modsDir}")
                }
            }
            
            // Task to deploy to your actual game's mods folder
            project.tasks.register<org.gradle.api.tasks.Copy>("deployToGame") {
                group = "hytale"
                description = "Deploy plugin to your Hytale game mods folder"

                val shadowJarTask = project.tasks.named("shadowJar")
                val jarTask = project.tasks.named("jar")
                dependsOn(shadowJarTask)
                inputs.files(shadowJarTask)
                inputs.files(jarTask)

                doFirst {
                    if (extension.serverPath.isEmpty()) {
                        throw org.gradle.api.GradleException("serverPath not set - can't find your game's mods folder")
                    }
                }

                from(shadowJarTask)
                into(File(extension.serverPath, "UserData/Mods"))

                doLast {
                    val modsPath = File(extension.serverPath, "UserData/Mods")
                    println("=========================================")
                    println("Plugin deployed to: ${modsPath.absolutePath}")
                    println("Now launch Hytale and your mod is ready!")
                    println("=========================================")
                }
            }

            // Task to generate API reference from Hytale Server JAR
            project.tasks.register("generateApiDocs") {
                group = "hytale"
                description = "Extract Hytale API signatures to HYTALE_API.md for Claude reference"

                doLast {
                    val hytaleJar = findHytaleServerJar(project)
                    if (hytaleJar == null) {
                        println("ERROR: Could not find Hytale Server JAR in Gradle cache")
                        println("Run './gradlew build' first to download dependencies")
                        return@doLast
                    }

                    println("Extracting API from: ${hytaleJar.absolutePath}")

                    val outputFile = File(project.rootDir, "HYTALE_API.md")
                    val apiContent = StringBuilder()

                    apiContent.appendLine("# Hytale Server API Reference")
                    apiContent.appendLine()
                    apiContent.appendLine("Auto-generated from: ${hytaleJar.name}")
                    apiContent.appendLine("Generated: ${java.time.LocalDateTime.now()}")
                    apiContent.appendLine()
                    apiContent.appendLine("Run `./gradlew generateApiDocs` to refresh after Hytale updates.")
                    apiContent.appendLine()
                    apiContent.appendLine("---")
                    apiContent.appendLine()

                    // Key packages we care about for modding
                    val relevantPackages = listOf(
                        "com.hypixel.hytale.server.core.command",
                        "com.hypixel.hytale.server.core.plugin",
                        "com.hypixel.hytale.server.core.universe",
                        "com.hypixel.hytale.server.core.Message",
                        "com.hypixel.hytale.component",
                        "com.hypixel.hytale.math.vector"
                    )

                    JarFile(hytaleJar).use { jar ->
                        val entries = jar.entries().toList()
                            .filter { it.name.endsWith(".class") }
                            .filter { entry ->
                                relevantPackages.any { pkg ->
                                    entry.name.startsWith(pkg.replace(".", "/"))
                                }
                            }
                            .sortedBy { it.name }

                        var currentPackage = ""

                        for (entry in entries) {
                            val className = entry.name
                                .removeSuffix(".class")
                                .replace("/", ".")

                            // Skip inner classes for cleaner output
                            if (className.contains("$")) continue

                            val pkg = className.substringBeforeLast(".")
                            if (pkg != currentPackage) {
                                currentPackage = pkg
                                apiContent.appendLine()
                                apiContent.appendLine("## $pkg")
                                apiContent.appendLine()
                            }

                            val simpleName = className.substringAfterLast(".")
                            apiContent.appendLine("- `$simpleName`")
                        }
                    }

                    // Add javap output for key classes
                    apiContent.appendLine()
                    apiContent.appendLine("---")
                    apiContent.appendLine()
                    apiContent.appendLine("## Key Class Signatures")
                    apiContent.appendLine()
                    apiContent.appendLine("Use `javap -cp <jar> <classname>` to get method signatures.")
                    apiContent.appendLine()
                    apiContent.appendLine("Example:")
                    apiContent.appendLine("```")
                    apiContent.appendLine("javap -cp \"${hytaleJar.absolutePath}\" com.hypixel.hytale.server.core.command.system.CommandContext")
                    apiContent.appendLine("```")

                    outputFile.writeText(apiContent.toString())
                    println("Generated: ${outputFile.absolutePath}")
                }
            }
        }
    }

    private fun findHytaleServerJar(project: Project): File? {
        // Search Gradle cache for Hytale Server JAR
        val gradleCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server")
        if (!gradleCache.exists()) return null

        // Find the latest version
        return gradleCache.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".jar") && !it.name.contains("sources") }
            .sortedByDescending { it.lastModified() }
            .firstOrNull()
    }
    
    private fun findServerJar(project: Project, extension: RunHytaleExtension): File? {
        // Check direct path first
        val directJar = File(project.projectDir, extension.serverJar)
        if (directJar.exists()) return directJar
        
        // Check Hytale installation
        if (extension.serverPath.isNotEmpty()) {
            // Check the actual Hytale installation structure
            val possibleLocations = listOf(
                "install/release/package/game/latest/Server/HytaleServer.jar",
                "install/pre-release/package/game/latest/Server/HytaleServer.jar",
                "Server/HytaleServer.jar",
                "server/HytaleServer.jar",
                "HytaleServer.jar"
            )
            for (loc in possibleLocations) {
                val jar = File(extension.serverPath, loc)
                if (jar.exists()) {
                    println("Found Hytale server at: ${jar.absolutePath}")
                    return jar
                }
            }
        }
        
        return null
    }
}
