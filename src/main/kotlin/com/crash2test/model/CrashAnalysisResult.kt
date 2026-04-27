package com.crash2test.model

sealed interface CrashAnalysisResult {
    data class Success(
        val parsedStackTrace: ParsedStackTrace,
        val resolvedFrames: List<ResolvedFrame>,
        val regressionTestLanguage: RegressionTestLanguage,
        val prompt: String,
        val analysisText: String,
    ) : CrashAnalysisResult

    data class Failure(
        val message: String,
        val parsedStackTrace: ParsedStackTrace? = null,
        val resolvedFrames: List<ResolvedFrame> = emptyList(),
        val regressionTestLanguage: RegressionTestLanguage = RegressionTestLanguage(
            displayName = "Kotlin",
            fenceTag = "kotlin",
            suggestedFileName = "GeneratedRegressionTest.kt",
        ),
    ) : CrashAnalysisResult
}
