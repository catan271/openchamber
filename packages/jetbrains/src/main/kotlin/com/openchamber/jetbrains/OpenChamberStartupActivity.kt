package com.openchamber.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class OpenChamberStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = OpenChamberProjectService.getInstance(project)
        service.getOpenCodeManager().start().await()
    }
}
