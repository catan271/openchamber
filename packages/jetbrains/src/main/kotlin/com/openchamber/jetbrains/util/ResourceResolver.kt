package com.openchamber.jetbrains.util

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

object ResourceResolver {

    private val logger = Logger.getInstance(ResourceResolver::class.java)

    /**
     * Resolve the webview asset directory and return a file:// URL pointing at
     * an on-disk copy of it. In development the directory is used directly;
     * when running from a JAR the resources are extracted to a temp directory.
     */
    fun resolveWebviewBaseUrl(): String {
        val dir = resolveWebviewDirectory()
        return dir.toUri().toString().removeSuffix("/")
    }

    fun resolveWebviewDirectory(): Path {
        // Development: resources are on disk next to the project.
        val devDir = File("src/main/resources/webview").toPath()
        if (devDir.exists() && devDir.isDirectory()) {
            return devDir
        }

        val classLoader = ResourceResolver::class.java.classLoader

        // Try the classpath resource root.
        val resourceUrl = classLoader.getResource("webview/index.html")
            ?: classLoader.getResource("webview")
            ?: throw IllegalStateException("Webview resources not found on classpath")

        val protocol = resourceUrl.protocol
        if (protocol == "file") {
            val file = File(resourceUrl.toURI())
            return if (file.isFile) file.parentFile?.toPath() ?: file.toPath() else file.toPath()
        }

        // Running from a JAR: extract to temp.
        return extractWebviewToTemp(classLoader)
    }

    private fun extractWebviewToTemp(classLoader: ClassLoader): Path {
        val tempRoot = Files.createTempDirectory("openchamber-webview-")
        tempRoot.toFile().deleteOnExit()

        val entries = classLoader.getResources("webview")
        while (entries.hasMoreElements()) {
            val url = entries.nextElement()
            if (url.protocol != "jar") continue
            val connection = url.openConnection() as? java.net.JarURLConnection ?: continue
            val jarFile = connection.jarFile
            jarFile.entries().asSequence()
                .filter { it.name.startsWith("webview/") && !it.isDirectory }
                .forEach { entry ->
                    val target = tempRoot.resolve(entry.name.removePrefix("webview/"))
                    Files.createDirectories(target.parent)
                    jarFile.getInputStream(entry).use { input ->
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }

        return tempRoot
    }
}
