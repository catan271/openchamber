package com.openchamber.jetbrains.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.openchamber.jetbrains.OpenChamberPlugin

class OpenSidebarAction : AnAction("Open OpenChamber") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(OpenChamberPlugin.TOOL_WINDOW_ID) ?: return
        toolWindow.show()
    }
}
