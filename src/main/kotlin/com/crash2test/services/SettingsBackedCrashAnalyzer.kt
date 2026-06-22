package com.crash2test.services

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.settings.Crash2TestSettingsState
import com.intellij.openapi.project.Project

class SettingsBackedCrashAnalyzer(
    private val project: Project,
    private val settings: Crash2TestSettingsState = Crash2TestSettingsState.getInstance(),
) : CrashAnalyzer {
    override fun analyzeStreaming(
        rawInput: String,
        onChunk: (String) -> Unit,
    ): CrashAnalysisResult {
        val service = CrashAnalysisService(
            stackTraceParser = StackTraceParser(),
            frameResolver = ProjectFileResolver(project),
            analysisGenerator = OllamaClient(settings.toOllamaClientConfig()),
        )
        return service.analyzeStreaming(rawInput, onChunk)
    }
}
