package com.openchamber.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class OpenChamberStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = OpenChamberProjectService.getInstance(project)
        // Do not hold IntelliJ project startup on the OpenCode readiness poll.
        // The manager publishes connecting/connected/error status to the panel.
        service.getOpenCodeManager().start()
    }
}
