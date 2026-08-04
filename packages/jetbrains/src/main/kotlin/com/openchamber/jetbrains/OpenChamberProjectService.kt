package com.openchamber.jetbrains

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.openchamber.jetbrains.bridge.BridgeMessageHandler
import com.openchamber.jetbrains.opencode.OpenCodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@Service(Service.Level.PROJECT)
class OpenChamberProjectService(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): OpenChamberProjectService = project.service()
        private val logger = Logger.getInstance(OpenChamberProjectService::class.java)
    }

    private val scope = CoroutineScope(SupervisorJob())
    private val openCodeManager = OpenCodeManager(project, scope)
    private val bridgeHandler = BridgeMessageHandler(project, openCodeManager, scope)
    private var panel: OpenChamberPanel? = null

    fun getOrCreatePanel(): OpenChamberPanel {
        if (panel == null) {
            panel = OpenChamberPanel(project, bridgeHandler, openCodeManager.getStatus())
            openCodeManager.setStatusListener { status, error ->
                panel?.postConnectionStatus(status, error)
            }
            bridgeHandler.setPanel(panel!!)
        }
        return panel!!
    }

    fun getPanel(): OpenChamberPanel? = panel

    fun getOpenCodeManager(): OpenCodeManager = openCodeManager

    fun getBridgeHandler(): BridgeMessageHandler = bridgeHandler

    override fun dispose() {
        scope.cancel()
        panel?.dispose()
        panel = null
        openCodeManager.dispose()
    }
}
