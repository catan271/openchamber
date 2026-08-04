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

class ExplainAction : AnAction("Explain with OpenChamber") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val document = editor.document
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText
        val languageId = file.fileType.name.lowercase()
        val relativePath = file.path.removePrefix(project.basePath ?: "")

        val prompt = if (!selectedText.isNullOrBlank()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            val lineRange = if (startLine == endLine) "$startLine" else "$startLine-$endLine"
            """Explain the following Code / Text:

$relativePath:$lineRange
```$languageId
$selectedText
```"""
        } else {
            "Explain the following Code / Text:\n\n$relativePath"
        }

        revealAndSend(project, "createSessionWithPrompt", buildJsonObject { put("prompt", prompt) })
    }

    private fun revealAndSend(project: com.intellij.openapi.project.Project, command: String, payload: JsonObject) {
        ToolWindowManager.getInstance(project).getToolWindow(OpenChamberPlugin.TOOL_WINDOW_ID)?.show()
        OpenChamberProjectService.getInstance(project).getPanel()?.postCommand(command, payload)
    }
}
