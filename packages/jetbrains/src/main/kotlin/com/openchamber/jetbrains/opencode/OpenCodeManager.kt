package com.openchamber.jetbrains.opencode

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.openchamber.jetbrains.util.OpenChamberSettings
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OpenCodeManager(
    private val project: Project,
    private val scope: CoroutineScope
) : Disposable {

    companion object {
        private val logger = Logger.getInstance(OpenCodeManager::class.java)
        private const val READY_TIMEOUT_MS = 30000L
        private const val READY_POLL_MS = 300L
    }

    private val settings = OpenChamberSettings.getInstance(project)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var process: Process? = null
    private var apiUrl: String? = null
    private var authHeader: String? = null
    private var workingDirectory: String = project.basePath ?: System.getProperty("user.home")
    private val status = AtomicReference("disconnected")
    private var statusListener: ((String, String?) -> Unit)? = null
    private var monitorJob: Job? = null

    fun setStatusListener(listener: (String, String?) -> Unit) {
        statusListener = listener
        listener(status.get(), null)
    }

    private fun emitStatus(newStatus: String, error: String? = null) {
        val previous = status.getAndSet(newStatus)
        if (previous != newStatus || error != null) {
            statusListener?.invoke(newStatus, error)
        }
    }

    fun start(): Deferred<Unit> = scope.async {
        emitStatus("connecting")
        try {
            stopInternal()

            val configuredUrl = settings.apiUrl?.trim()
            if (!configuredUrl.isNullOrEmpty()) {
                apiUrl = configuredUrl.removeSuffix("/")
                authHeader = buildAuthHeaderFromEnv()
                workingDirectory = project.basePath ?: workingDirectory
                emitStatus("connected")
                return@async
            }

            val cliPath = resolveCliPath()
            if (cliPath == null) {
                emitStatus("error", "OpenCode CLI not found. Configure path in Settings > OpenChamber.")
                return@async
            }

            val port = findFreePort()
            val password = generatePassword()
            val username = System.getenv("OPENCODE_SERVER_USERNAME")?.takeIf { it.isNotBlank() } ?: "opencode"
            authHeader = "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
            apiUrl = "http://127.0.0.1:$port"

            val command = buildCliCommand(cliPath, port, password)
            val builder = ProcessBuilder(command)
                .directory(File(workingDirectory))
                .redirectErrorStream(true)
            val env = builder.environment()
            env["OPENCODE_SERVER_PASSWORD"] = password
            env["OPENCODE_SERVER_USERNAME"] = username

            process = builder.start()

            monitorJob = scope.launch(Dispatchers.IO) {
                readProcessLogs()
            }

            val ready = waitForReady()
            if (ready) {
                emitStatus("connected")
            } else {
                emitStatus("error", "OpenCode server did not become ready in time.")
                stopInternal()
            }
        } catch (e: Exception) {
            logger.warn("Failed to start OpenCode", e)
            emitStatus("error", e.message ?: "Unknown error starting OpenCode")
        }
    }

    fun stop() {
        runBlocking {
            stopInternal()
        }
    }

    private suspend fun stopInternal() {
        monitorJob?.cancelAndJoin()
        monitorJob = null
        process?.destroyForcibly()
        process = null
        apiUrl = null
        authHeader = null
        emitStatus("disconnected")
    }

    fun restart(): Deferred<Unit> = scope.async {
        stopInternal()
        start().await()
    }

    fun getApiUrl(): String? = apiUrl

    fun getAuthHeaders(): Map<String, String> {
        return authHeader?.let { mapOf("Authorization" to it) } ?: emptyMap()
    }

    fun getWorkingDirectory(): String = workingDirectory

    fun setWorkingDirectory(path: String) {
        workingDirectory = path
    }

    fun isCliAvailable(): Boolean {
        return settings.apiUrl?.isNotBlank() == true || resolveCliPath() != null
    }

    fun getStatus(): String = status.get()

    override fun dispose() {
        stop()
    }

    private fun resolveCliPath(): String? {
        val configured = settings.opencodeBinary?.trim()
        if (!configured.isNullOrEmpty()) {
            val file = File(configured)
            if (file.exists() && file.canExecute()) return configured
        }

        val candidates = listOf("opencode", "opencode.exe", "opencode.cmd", "opencode.bat")
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.exists() && file.canExecute()) return file.absolutePath
            }
        }
        return null
    }

    private fun buildCliCommand(cliPath: String, port: Int, password: String): List<String> {
        val base = listOf(cliPath, "serve", "--hostname", "127.0.0.1", "--port", port.toString())
        val isWindowsBatchScript = System.getProperty("os.name").orEmpty().lowercase().contains("win") &&
            (cliPath.endsWith(".cmd", ignoreCase = true) || cliPath.endsWith(".bat", ignoreCase = true))
        return if (isWindowsBatchScript) {
            listOf("cmd.exe", "/c", "call") + base
        } else {
            base
        }
    }

    private fun buildAuthHeaderFromEnv(): String? {
        val username = System.getenv("OPENCODE_SERVER_USERNAME")?.takeIf { it.isNotBlank() } ?: "opencode"
        val password = System.getenv("OPENCODE_SERVER_PASSWORD")?.takeIf { it.isNotBlank() }
        return password?.let {
            "Basic " + Base64.getEncoder().encodeToString("$username:$it".toByteArray(Charsets.UTF_8))
        }
    }

    private suspend fun waitForReady(): Boolean {
        val url = apiUrl ?: return false
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive == false) {
                return false
            }
            try {
                val request = Request.Builder()
                    .url("$url/health")
                    .header("Accept", "application/json")
                    .apply { authHeader?.let { header("Authorization", it) } }
                    .build()
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                if (response.isSuccessful) {
                    return true
                }
            } catch (e: Exception) {
                // not ready yet
            }
            delay(READY_POLL_MS)
        }
        return false
    }

    private suspend fun readProcessLogs() {
        val proc = process ?: return
        try {
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logger.info("[OpenCode] $line")
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                logger.warn("Error reading OpenCode process logs", e)
            }
        }
    }

    private fun findFreePort(): Int {
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress("127.0.0.1", 0))
            return socket.localPort
        }
    }

    private fun generatePassword(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            .replace("+", "-")
            .replace("/", "_")
    }
}
