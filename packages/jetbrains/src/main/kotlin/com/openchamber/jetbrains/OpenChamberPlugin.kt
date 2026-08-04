package com.openchamber.jetbrains

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class OpenChamberPlugin : Disposable {

    companion object {
        const val TOOL_WINDOW_ID = "OpenChamber"
        private val logger = Logger.getInstance(OpenChamberPlugin::class.java)

        fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
    }

    override fun dispose() {
        logger.info("OpenChamber plugin disposed")
    }
}
