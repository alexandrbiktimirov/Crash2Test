package com.crash2test.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager

class Crash2TestStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.invokeLater {
            toolWindowManager.getToolWindow("Crash2Test")?.show()
        }
    }
}
