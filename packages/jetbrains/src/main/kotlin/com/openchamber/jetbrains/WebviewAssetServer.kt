package com.openchamber.jetbrains

import com.intellij.openapi.Disposable
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/** Serves bundled webview assets from a same-origin HTTP URL for JCEF ES modules. */
class WebviewAssetServer(webviewDirectory: Path) : Disposable {

    private val root = webviewDirectory.toAbsolutePath().normalize()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val indexUrl: String
        get() = "http://127.0.0.1:${server.address.port}/index.html"

    init {
        server.createContext("/") { exchange ->
            if (exchange.requestMethod != "GET") {
                exchange.sendResponseHeaders(405, -1)
                exchange.close()
                return@createContext
            }

            val requestPath = exchange.requestURI.path.removePrefix("/").ifBlank { "index.html" }
            val asset = root.resolve(requestPath).normalize()
            if (!asset.startsWith(root) || !asset.isRegularFile()) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return@createContext
            }

            val content = Files.readAllBytes(asset)
            exchange.responseHeaders.set("Content-Type", contentType(asset))
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
        }
        server.start()
    }

    override fun dispose() {
        server.stop(0)
    }

    private fun contentType(path: Path): String = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "html" -> "text/html; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "wasm" -> "application/wasm"
        else -> "application/octet-stream"
    }
}
