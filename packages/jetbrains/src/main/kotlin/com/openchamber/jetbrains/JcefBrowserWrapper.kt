package com.openchamber.jetbrains

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component

class JcefBrowserWrapper : Disposable {

    companion object {
        private val logger = Logger.getInstance(JcefBrowserWrapper::class.java)

        fun isSupported(): Boolean = JBCefApp.isSupported()
    }

    private val browser: JBCefBrowser
    private val jsQuery: JBCefJSQuery
    private var messageHandler: ((String) -> Unit)? = null

    val component: Component
        get() = browser.component

    init {
        if (!JBCefApp.isSupported()) {
            throw IllegalStateException("JCEF is not supported in this IDE")
        }

        browser = JBCefBrowser()
        jsQuery = JBCefJSQuery.create(browser)

        jsQuery.addHandler { raw ->
            try {
                messageHandler?.invoke(raw)
            } catch (e: Exception) {
                logger.warn("Error handling JS query", e)
            }
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                injectBridgePostFunction()
            }
        }, browser.cefBrowser)

        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser?,
                messageLevel: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                if (message != null) {
                    logger.debug("JCEF console [$source:$line]: $message")
                }
                return true
            }
        }, browser.cefBrowser)
    }

    private fun injectBridgePostFunction() {
        val query = jsQuery.inject("JSON.stringify(message)")
        executeJavaScript(
            """
            window.__OPENCHAMBER_JCEF_POST__ = function(message) {
              $query;
            };
            """.trimIndent()
        )
    }

    fun loadHtml(html: String, url: String) {
        browser.loadHTML(html, url)
    }

    fun loadUrl(url: String) {
        browser.loadURL(url)
    }

    fun executeJavaScript(script: String) {
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
    }

    fun onMessage(handler: (String) -> Unit) {
        messageHandler = handler
    }

    override fun dispose() {
        jsQuery.dispose()
        browser.dispose()
    }
}
