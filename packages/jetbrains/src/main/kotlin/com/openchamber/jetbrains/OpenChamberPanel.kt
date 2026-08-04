package com.openchamber.jetbrains

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.openchamber.jetbrains.bridge.BridgeMessageHandler
import com.openchamber.jetbrains.bridge.BridgeRequest
import com.openchamber.jetbrains.bridge.BridgeResponse
import com.openchamber.jetbrains.util.OpenChamberSettings
import com.openchamber.jetbrains.util.ResourceResolver
import com.openchamber.jetbrains.util.themeVariant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

class OpenChamberPanel(
    private val project: Project,
    private val bridgeHandler: BridgeMessageHandler,
    private val initialConnectionStatus: String
) : com.intellij.openapi.Disposable {

    companion object {
        private val logger = Logger.getInstance(OpenChamberPanel::class.java)
        private val json = Json { ignoreUnknownKeys = true }
    }

    val component: JPanel = JPanel(BorderLayout())
    private var browser: JcefBrowserWrapper? = null
    private var assetServer: WebviewAssetServer? = null
    private val settings = OpenChamberSettings.getInstance(project)
    @Volatile private var connectionStatus = initialConnectionStatus
    @Volatile private var connectionError: String? = null

    init {
        initialize()
    }

    private fun initialize() {
        if (!JcefBrowserWrapper.isSupported()) {
            component.add(
                JBLabel("OpenChamber requires JCEF (JetBrains Chromium Embedded Framework).").apply {
                    foreground = JBColor.RED
                },
                BorderLayout.CENTER
            )
            return
        }

        val initializedBrowser = JcefBrowserWrapper()
        browser = initializedBrowser

        initializedBrowser.let { browser ->
            Disposer.register(project, browser)
            component.add(browser.component, BorderLayout.CENTER)

            browser.onMessage { raw ->
                try {
                    val request = json.decodeFromString<BridgeRequest>(raw)
                    bridgeHandler.handle(request) { response ->
                        postResponse(response)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse bridge message: $raw", e)
                }
            }
            browser.onPageLoaded {
                dispatchConnectionStatus()
            }
        }

        loadHtml()
    }

    private fun loadHtml() {
        // A reload can be queued on the EDT while the project is closing. In
        // that case the panel has already released its JCEF browser and there
        // is no webview left to load.
        val activeBrowser = browser ?: return

        try {
            val webviewDir = ResourceResolver.resolveWebviewDirectory().toFile()
            val html = buildWebviewHtml(activeBrowser.postMessageQuery())
            File(webviewDir, "index.html").writeText(html, Charsets.UTF_8)
            assetServer?.dispose()
            assetServer = WebviewAssetServer(webviewDir.toPath())
            activeBrowser.loadUrl(assetServer!!.indexUrl)
        } catch (e: Exception) {
            logger.error("Failed to load OpenChamber webview", e)
            component.removeAll()
            component.add(
                JBLabel("Failed to load OpenChamber webview: ${e.message}").apply {
                    foreground = JBColor.RED
                },
                BorderLayout.CENTER
            )
        }
    }

    fun reload() {
        ApplicationManager.getApplication().invokeLater {
            loadHtml()
        }
    }

    fun postConnectionStatus(status: String, error: String?) {
        connectionStatus = status
        connectionError = error
        dispatchConnectionStatus()
    }

    private fun dispatchConnectionStatus() {
        postRaw(
            json.encodeToString(buildJsonObject {
                put("type", "connectionStatus")
                put("status", connectionStatus)
                if (connectionError == null) {
                    put("error", JsonNull)
                } else {
                    put("error", connectionError!!)
                }
            })
        )
    }

    fun postCommand(command: String, payload: JsonElement? = null) {
        postRaw(
            json.encodeToString(buildJsonObject {
                put("type", "command")
                put("command", command)
                put("payload", payload ?: JsonNull)
            })
        )
    }

    private fun postResponse(response: BridgeResponse) {
        postRaw(json.encodeToString(response))
    }

    fun postRaw(message: String) {
        ApplicationManager.getApplication().invokeLater {
            browser?.executeJavaScript(
                "window.dispatchEvent(new MessageEvent('message', { data: $message }));"
            )
        }
    }

    private fun buildWebviewHtml(postMessageQuery: String): String {
        val workspaceFolder = project.basePath?.replace("\\", "/") ?: ""
        val theme = themeVariant()
        val initialStatus = connectionStatus
        val extensionVersion = "1.14.1"
        val platform = System.getProperty("os.name", "")
        val arch = System.getProperty("os.arch", "")
        val configJson = json.encodeToString(buildJsonObject {
            put("workspaceFolder", workspaceFolder)
            putJsonArray("workspaceFolders") {
                add(buildJsonObject {
                    put("name", project.name)
                    put("path", workspaceFolder)
                })
            }
            put("theme", theme)
            put("connectionStatus", initialStatus)
            put("cliAvailable", false)
            put("extensionVersion", extensionVersion)
            put("platform", platform)
            put("arch", arch)
            put("panelType", "chat")
            put("viewMode", "sidebar")
        }).replace("<", "\\u003c")

        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    html, body, #root { height: 100%; width: 100%; margin: 0; padding: 0; }
    body { overflow: hidden; background: ${backgroundColor()}; font-family: ${fontFamily()}; color: ${foregroundColor()}; }
    #initial-loading { position: fixed; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; z-index: 9999; background: ${backgroundColor()}; transition: opacity 0.3s ease-out; }
    #initial-loading.fade-out { opacity: 0; pointer-events: none; }
    .status-text { font-size: 13px; color: ${foregroundColor()}; text-align: center; }
    .error-text { font-size: 12px; color: #f48771; text-align: center; max-width: 280px; }
  </style>
  <title>OpenChamber</title>
</head>
<body>
  <div id="initial-loading">
    <svg class="logo" width="70" height="70" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path stroke="${foregroundColor()}" fill="${foregroundColor()}" fill-opacity="0.15" d="M50 50 L8.432 26 L8.432 74 L50 98 Z" stroke-width="2" stroke-linejoin="round"/>
      <path stroke="${foregroundColor()}" fill="${foregroundColor()}" fill-opacity="0.15" d="M50 50 L91.568 26 L91.568 74 L50 98 Z" stroke-width="2" stroke-linejoin="round"/>
      <path stroke="${foregroundColor()}" fill="none" d="M50 2 L8.432 26 L50 50 L91.568 26 Z" stroke-width="2" stroke-linejoin="round"/>
    </svg>
    <div class="status-text" id="loading-status"></div>
  </div>
  <div id="root"></div>
  <script>
    window.process = window.process || { env: { NODE_ENV: 'production' }, platform: '', version: '', browser: true };
    window.__JETBRAINS_CONFIG__ = $configJson;
    window.__VSCODE_CONFIG__ = window.__JETBRAINS_CONFIG__;
     window.__OPENCHAMBER_HOME__ = "${workspaceFolder.replace("\\", "\\\\")}";
     window.__OPENCHAMBER_JCEF_POST__ = function(message) {
       $postMessageQuery;
     };

     function acquireVsCodeApi() {
      return window.__OPENCHAMBER_JETBRAINS_BRIDGE__;
    }

    window.__OPENCHAMBER_JETBRAINS_BRIDGE__ = (function() {
       let onMessageCallback = null;
       return {
         postMessage: function(message) {
           window.__OPENCHAMBER_JCEF_POST__(message);
         },
        getState: function() { return null; },
        setState: function() {},
        onMessage: function(callback) { onMessageCallback = callback; },
        _dispatch: function(raw) {
          try {
            var msg = JSON.parse(raw);
            if (onMessageCallback) onMessageCallback(msg);
          } catch (e) {}
        }
      };
    })();

    window.addEventListener('message', function(event) {
      if (event.data && event.data.type === 'connectionStatus') {
        var statusEl = document.getElementById('loading-status');
        if (statusEl) {
          if (event.data.status === 'error') {
            statusEl.textContent = event.data.error || 'Connection error';
            statusEl.classList.add('error-text');
          } else {
            statusEl.textContent = '';
            statusEl.classList.remove('error-text');
          }
        }
      }
    });
  </script>
  <script type="module" src="./assets/index.js"></script>
</body>
</html>""".trimIndent()
    }

    private fun backgroundColor(): String {
        val jb = JBColor.namedColor("Panel.background", JBColor.WHITE)
        return "#%02x%02x%02x".format(jb.red, jb.green, jb.blue)
    }

    private fun foregroundColor(): String {
        val jb = JBColor.namedColor("Label.foreground", JBColor.BLACK)
        return "#%02x%02x%02x".format(jb.red, jb.green, jb.blue)
    }

    private fun fontFamily(): String {
        return "'${JBUI.Fonts.label().fontName}', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    }

    override fun dispose() {
        assetServer?.dispose()
        assetServer = null
        browser?.dispose()
        browser = null
    }
}
