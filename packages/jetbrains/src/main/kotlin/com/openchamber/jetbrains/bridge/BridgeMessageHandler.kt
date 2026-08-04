package com.openchamber.jetbrains.bridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.openchamber.jetbrains.OpenChamberPanel
import com.openchamber.jetbrains.opencode.OpenCodeManager
import com.openchamber.jetbrains.util.OpenChamberSettings
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BridgeMessageHandler(
    private val project: Project,
    private val openCodeManager: OpenCodeManager,
    private val scope: CoroutineScope
) {

    companion object {
        private val logger = Logger.getInstance(BridgeMessageHandler::class.java)
        private val json = Json { ignoreUnknownKeys = true }
        private const val GITHUB_DISABLED = "GitHub integration is not available in the JetBrains runtime."
    }

    private lateinit var panel: OpenChamberPanel
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val sseFactory = EventSources.createFactory(httpClient)
    private val sseStreams = mutableMapOf<String, EventSource>()
    private val sseCounter = AtomicInteger(0)

    fun setPanel(panel: OpenChamberPanel) {
        this.panel = panel
    }

    fun handle(request: BridgeRequest, respond: (BridgeResponse) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val response = try {
                when (request.type) {
                    "api:proxy" -> handleProxy(request)
                    "api:proxy:abort" -> handleProxyAbort(request)
                    "api:session:message" -> handleSessionMessage(request)
                    "api:sse:start" -> handleSseStart(request)
                    "api:sse:stop" -> handleSseStop(request)
                    "api:config/settings:get" -> handleSettingsGet(request)
                    "api:config/settings:save" -> handleSettingsSave(request)
                    "api:config/reload" -> handleConfigReload(request)
                    "api:fs:list" -> handleFsList(request)
                    "api:fs:mkdir" -> handleFsMkdir(request)
                    "api:fs:read" -> handleFsRead(request)
                    "api:fs:write" -> handleFsWrite(request)
                    "api:fs:stat" -> handleFsStat(request)
                    "api:fs:delete" -> handleFsDelete(request)
                    "api:fs:rename" -> handleFsRename(request)
                    "api:fs:search" -> handleFsSearch(request)
                    "api:fs:reveal" -> handleFsReveal(request)
                    "api:fs/home" -> handleFsHome(request)
                    "api:files/pick" -> handleFilesPick(request)
                    "api:opencode/version" -> handleOpenCodeVersion(request)
                    "api:opencode/directory" -> handleOpenCodeDirectory(request)
                    "api:models/metadata" -> handleModelsMetadata(request)
                    "api:notifications:claim" -> handleNotificationClaim(request)
                    "api:notifications/auto-accept" -> handleNotificationAutoAccept(request)
                    "api:system/info" -> handleSystemInfo(request)
                    "editor:openFile" -> handleEditorOpenFile(request)
                    "vscode:openExternalUrl" -> handleOpenExternalUrl(request)
                    "vscode:command" -> handleVSCodeCommand(request)
                    else -> {
                        if (request.type.startsWith("api:github/")) {
                            githubDisabled(request)
                        } else {
                            unknown(request)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Bridge handler error for ${request.type}", e)
                BridgeResponse(request.id, request.type, false, error = e.message ?: "Unknown error")
            }
            withContext(Dispatchers.Main) { respond(response) }
        }
    }

    private fun unknown(request: BridgeRequest) = BridgeResponse(
        request.id, request.type, false, error = "Unknown message type: ${request.type}"
    )

    private fun githubDisabled(request: BridgeRequest) = BridgeResponse(
        request.id, request.type, false, error = GITHUB_DISABLED
    )

    private fun handleProxy(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val method = payload["method"]?.jsonPrimitive?.content ?: "GET"
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val headers = payload["headers"]?.jsonObject ?: JsonObject(emptyMap())
        val bodyBase64 = payload["bodyBase64"]?.jsonPrimitive?.contentOrNull

        val url = buildApiUrl(path) ?: return unavailable(request)
        val requestBuilder = Request.Builder().url(url)

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value.jsonPrimitive.content)
        }
        openCodeManager.getAuthHeaders().forEach { (k, v) ->
            if (!headers.containsKey(k)) requestBuilder.header(k, v)
        }

        if (method != "GET") {
            val bodyBytes = bodyBase64?.let { Base64.getDecoder().decode(it) } ?: ByteArray(0)
            val mediaType = headers["Content-Type"]?.jsonPrimitive?.content?.toMediaType()
                ?: "application/json".toMediaType()
            requestBuilder.method(method, bodyBytes.toRequestBody(mediaType))
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.bytes() ?: ByteArray(0)
            val bodyText = responseBody.toString(Charsets.UTF_8)
            val isText = response.header("Content-Type")?.contains("json") == true ||
                    response.header("Content-Type")?.contains("text") == true
            val result = buildJsonObject {
                put("status", response.code)
                putJsonObject("headers") {
                    response.headers.forEach { (name, value) -> put(name, value) }
                }
                if (isText) {
                    put("bodyText", bodyText)
                } else {
                    put("bodyBase64", Base64.getEncoder().encodeToString(responseBody))
                }
            }
            return BridgeResponse(request.id, request.type, true, result)
        }
    }

    private fun handleProxyAbort(request: BridgeRequest): BridgeResponse {
        // Best-effort abort placeholder: OkHttp calls are not trivially abortable by ID here.
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("aborted" to JsonPrimitive(false))))
    }

    private fun handleSessionMessage(request: BridgeRequest): BridgeResponse {
        return handleProxy(request.copy(type = "api:proxy"))
    }

    private fun handleSseStart(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val headers = payload["headers"]?.jsonObject ?: JsonObject(emptyMap())
        val url = buildApiUrl(path) ?: return unavailable(request)

        val streamId = "sse_${sseCounter.incrementAndGet()}_${System.currentTimeMillis()}"
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v.jsonPrimitive.content) }
        openCodeManager.getAuthHeaders().forEach { (k, v) ->
            if (!headers.containsKey(k)) requestBuilder.header(k, v)
        }

        val eventSource = sseFactory.newEventSource(requestBuilder.build(), object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                panel.postRaw(json.encodeToString(buildJsonObject {
                    put("type", "api:sse:chunk")
                    put("streamId", streamId)
                    put("chunk", "")
                }))
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                panel.postRaw(json.encodeToString(buildJsonObject {
                    put("type", "api:sse:chunk")
                    put("streamId", streamId)
                    put("chunk", "data: $data\n\n")
                }))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                panel.postRaw(json.encodeToString(buildJsonObject {
                    put("type", "api:sse:end")
                    put("streamId", streamId)
                    put("error", t?.message ?: "SSE failure")
                }))
            }

            override fun onClosed(eventSource: EventSource) {
                panel.postRaw(json.encodeToString(buildJsonObject {
                    put("type", "api:sse:end")
                    put("streamId", streamId)
                }))
            }
        })
        sseStreams[streamId] = eventSource

        val result = buildJsonObject {
            put("status", 200)
            putJsonObject("headers") { put("content-type", "text/event-stream") }
            put("streamId", streamId)
        }
        return BridgeResponse(request.id, request.type, true, result)
    }

    private fun handleSseStop(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val streamId = payload["streamId"]?.jsonPrimitive?.contentOrNull
        streamId?.let { sseStreams.remove(it)?.cancel() }
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("stopped" to JsonPrimitive(true))))
    }

    private fun handleSettingsGet(request: BridgeRequest): BridgeResponse {
        val settings = OpenChamberSettings.getInstance(project)
        val data = buildJsonObject {
            put("themeVariant", settings.themeVariant)
            put("lastDirectory", openCodeManager.getWorkingDirectory())
            put("apiUrl", settings.apiUrl ?: "")
            put("opencodeBinary", settings.opencodeBinary ?: "")
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleSettingsSave(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val settings = OpenChamberSettings.getInstance(project)
        payload["apiUrl"]?.jsonPrimitive?.contentOrNull?.let { settings.apiUrl = it }
        payload["opencodeBinary"]?.jsonPrimitive?.contentOrNull?.let { settings.opencodeBinary = it }
        payload["themeVariant"]?.jsonPrimitive?.contentOrNull?.let { settings.themeVariant = it }
        return BridgeResponse(request.id, request.type, true, payload)
    }

    private fun handleConfigReload(request: BridgeRequest): BridgeResponse {
        scope.launch { openCodeManager.restart().await() }
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("restarted" to JsonPrimitive(true))))
    }

    private fun handleFsList(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val dir = File(path)
        val entries = dir.listFiles()?.sortedBy { it.name }?.map { file ->
            buildJsonObject {
                put("name", file.name)
                put("path", file.absolutePath.replace("\\", "/"))
                put("isDirectory", file.isDirectory)
            }
        } ?: emptyList()
        val data = buildJsonObject {
            put("directory", dir.absolutePath.replace("\\", "/"))
            putJsonArray("entries") { entries.forEach { add(it) } }
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleFsMkdir(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val success = File(path).mkdirs()
        return BridgeResponse(request.id, request.type, success, JsonObject(mapOf("success" to JsonPrimitive(success), "path" to JsonPrimitive(path.replace("\\", "/")))))
    }

    private fun handleFsRead(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val content = File(path).readText(Charsets.UTF_8)
        val data = buildJsonObject {
            put("content", content)
            put("path", path.replace("\\", "/"))
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleFsWrite(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val content = payload["content"]?.jsonPrimitive?.content ?: ""
        File(path).writeText(content, Charsets.UTF_8)
        val data = buildJsonObject {
            put("success", true)
            put("path", path.replace("\\", "/"))
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleFsStat(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val file = File(path)
        val data = buildJsonObject {
            put("path", path.replace("\\", "/"))
            put("isFile", file.isFile)
            put("size", file.length())
            put("mtimeMs", file.lastModified())
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleFsDelete(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val success = File(path).deleteRecursively()
        return BridgeResponse(request.id, request.type, success, JsonObject(mapOf("success" to JsonPrimitive(success))))
    }

    private fun handleFsRename(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val oldPath = payload["oldPath"]?.jsonPrimitive?.content ?: return badRequest(request)
        val newPath = payload["newPath"]?.jsonPrimitive?.content ?: return badRequest(request)
        val success = File(oldPath).renameTo(File(newPath))
        return BridgeResponse(request.id, request.type, success, JsonObject(mapOf("success" to JsonPrimitive(success), "path" to JsonPrimitive(newPath.replace("\\", "/")))))
    }

    private fun handleFsSearch(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val directory = payload["directory"]?.jsonPrimitive?.content ?: return badRequest(request)
        val query = payload["query"]?.jsonPrimitive?.content ?: ""
        val limit = payload["limit"]?.jsonPrimitive?.intOrNull ?: 20
        val results = searchFiles(File(directory), query, limit)
        val data = buildJsonObject {
            putJsonArray("files") {
                results.forEach { file ->
                    add(buildJsonObject {
                        put("path", file.absolutePath.replace("\\", "/"))
                        put("relativePath", file.relativeTo(File(directory)).path.replace("\\", "/"))
                    })
                }
            }
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun searchFiles(root: File, query: String, limit: Int): List<File> {
        val results = mutableListOf<File>()
        root.walkTopDown()
            .filter { it.isFile && it.name.contains(query, ignoreCase = true) }
            .take(limit)
            .forEach { results.add(it) }
        return results
    }

    private fun handleFsReveal(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        ApplicationManager.getApplication().invokeLater {
            val file = LocalFileSystem.getInstance().findFileByPath(path)
            file?.let { VfsUtil.markDirtyAndRefresh(false, false, false, it) }
        }
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("success" to JsonPrimitive(true))))
    }

    private fun handleFsHome(request: BridgeRequest): BridgeResponse {
        val home = System.getProperty("user.home")
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("home" to JsonPrimitive(home.replace("\\", "/")))))
    }

    private fun handleFilesPick(request: BridgeRequest): BridgeResponse {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
        val files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null).choose(project)
        val paths = files.map { it.path.replace("\\", "/") }
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("paths" to JsonArray(paths.map { JsonPrimitive(it) }))))
    }

    private fun handleOpenCodeVersion(request: BridgeRequest): BridgeResponse {
        val url = openCodeManager.getApiUrl() ?: return unavailable(request)
        return try {
            val req = Request.Builder().url("$url/api/opencode/version")
                .apply { openCodeManager.getAuthHeaders().forEach { (k, v) -> header(k, v) } }
                .build()
            httpClient.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val parsed = json.parseToJsonElement(body)
                BridgeResponse(request.id, request.type, response.isSuccessful, parsed)
            }
        } catch (e: Exception) {
            BridgeResponse(request.id, request.type, false, error = e.message)
        }
    }

    private fun handleOpenCodeDirectory(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        openCodeManager.setWorkingDirectory(path)
        val data = buildJsonObject {
            put("directory", path.replace("\\", "/"))
            put("success", true)
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleModelsMetadata(request: BridgeRequest): BridgeResponse {
        return BridgeResponse(request.id, request.type, true, JsonObject(emptyMap()))
    }

    private fun handleNotificationClaim(request: BridgeRequest): BridgeResponse {
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("claimed" to JsonPrimitive(true))))
    }

    private fun handleNotificationAutoAccept(request: BridgeRequest): BridgeResponse {
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("success" to JsonPrimitive(true))))
    }

    private fun handleSystemInfo(request: BridgeRequest): BridgeResponse {
        val data = buildJsonObject {
            put("openchamberVersion", "1.14.1")
            put("runtime", "jetbrains")
            put("platform", System.getProperty("os.name"))
            put("arch", System.getProperty("os.arch"))
        }
        return BridgeResponse(request.id, request.type, true, data)
    }

    private fun handleEditorOpenFile(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val path = payload["path"]?.jsonPrimitive?.content ?: return badRequest(request)
        val line = payload["line"]?.jsonPrimitive?.intOrNull ?: 0
        val column = payload["column"]?.jsonPrimitive?.intOrNull ?: 0
        ApplicationManager.getApplication().invokeLater {
            val file = LocalFileSystem.getInstance().findFileByPath(path)
            file?.let {
                val descriptor = OpenFileDescriptor(project, it, line.coerceAtLeast(0), column.coerceAtLeast(0))
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
            }
        }
        return BridgeResponse(request.id, request.type, true)
    }

    private fun handleOpenExternalUrl(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val url = payload["url"]?.jsonPrimitive?.content ?: return badRequest(request)
        ApplicationManager.getApplication().invokeLater {
            com.intellij.ide.BrowserUtil.browse(url)
        }
        return BridgeResponse(request.id, request.type, true)
    }

    private fun handleVSCodeCommand(request: BridgeRequest): BridgeResponse {
        val payload = request.payload?.jsonObject ?: return badRequest(request)
        val command = payload["command"]?.jsonPrimitive?.content ?: return badRequest(request)
        ApplicationManager.getApplication().invokeLater {
            Messages.showInfoMessage(project, "Command: $command", "OpenChamber")
        }
        return BridgeResponse(request.id, request.type, true, JsonObject(mapOf("result" to JsonNull)))
    }

    private fun buildApiUrl(path: String): String? {
        val base = openCodeManager.getApiUrl() ?: return null
        val normalized = path.removePrefix("/")
        return "$base/$normalized"
    }

    private fun badRequest(request: BridgeRequest) = BridgeResponse(
        request.id, request.type, false, error = "Bad request payload"
    )

    private fun unavailable(request: BridgeRequest) = BridgeResponse(
        request.id, request.type, false, error = "OpenCode API not available"
    )
}
