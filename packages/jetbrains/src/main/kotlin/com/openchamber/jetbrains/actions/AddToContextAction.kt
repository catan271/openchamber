package com.openchamber.jetbrains.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.openchamber.jetbrains.OpenChamberPlugin
import com.openchamber.jetbrains.OpenChamberProjectService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AddToContextAction : AnAction("Add to OpenChamber Context") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
        val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
        val lineRange = if (startLine == endLine) "$startLine" else "$startLine-$endLine"
        val filename = "${file.name}:$lineRange"

        revealAndSend(project, "addContextSelection", buildJsonObject {
            put("filePath", file.path)
            put("filename", filename)
            put("text", selectedText)
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true
    }

    private fun revealAndSend(project: com.intellij.openapi.project.Project, command: String, payload: JsonObject) {
        ToolWindowManager.getInstance(project).getToolWindow(OpenChamberPlugin.TOOL_WINDOW_ID)?.show()
        OpenChamberProjectService.getInstance(project).getPanel()?.postCommand(command, payload)
    }
}
