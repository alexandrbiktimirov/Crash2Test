package com.crash2test.ui

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.RegressionTestLanguage
import com.crash2test.model.ResolvedFrame

class AnalysisResultRenderer {
    private val regressionTestCodeExtractor = RegressionTestCodeExtractor()

    fun initialState(): Crash2TestViewState = Crash2TestViewState(
        statusMessage = "Paste a stack trace or runtime error, then press Analyze.",
        resultText = "",
        canAnalyze = true,
    )

    fun loadingState(): Crash2TestViewState = Crash2TestViewState(
        statusMessage = "Running crash analysis with local Ollama...",
        resultText = "",
        canAnalyze = false,
    )

    fun render(result: CrashAnalysisResult): Crash2TestViewState = when (result) {
        is CrashAnalysisResult.Success -> regressionTestCodeExtractor.extract(result.analysisText).let { extracted ->
            Crash2TestViewState(
                statusMessage = "Analysis complete.",
                resultText = extracted.markdown,
                canAnalyze = true,
                clickableFrames = clickableFrames(result.resolvedFrames),
                regressionTestCode = extracted.regressionTestCode?.code,
                regressionTestFenceTag = extracted.regressionTestCode?.fenceTag ?: result.regressionTestLanguage.fenceTag,
                regressionTestFileName = buildRegressionTestFileName(
                    extracted.regressionTestCode?.fenceTag ?: result.regressionTestLanguage.fenceTag,
                    result.regressionTestLanguage.suggestedFileName,
                ),
            )
        }

        is CrashAnalysisResult.Failure -> Crash2TestViewState(
            statusMessage = result.message,
            resultText = formatFailureDetails(result.parsedStackTrace, result.resolvedFrames),
            canAnalyze = true,
            clickableFrames = clickableFrames(result.resolvedFrames),
            regressionTestCode = fallbackRegressionTestCode(result.regressionTestLanguage),
            regressionTestFenceTag = result.regressionTestLanguage.fenceTag,
            regressionTestFileName = result.regressionTestLanguage.suggestedFileName,
        )
    }

    private fun clickableFrames(resolvedFrames: List<ResolvedFrame>): List<ResolvedFrame> =
        resolvedFrames.filter { it.status == ResolvedFrame.ResolutionStatus.RESOLVED }

    private fun formatFailureDetails(
        parsed: ParsedStackTrace?,
        resolvedFrames: List<ResolvedFrame>,
    ): String {
        if (parsed == null) {
            return ""
        }

        val summary = buildString {
            append(parsed.exceptionType ?: "Exception type not detected")
            parsed.exceptionMessage?.let { append(": $it") }
            append(" (${parsed.frames.size} frame(s))")
        }

        val likelyCause = parsed.frames
            .take(MAX_SUMMARY_FRAMES)
            .mapIndexed { index, frame -> "${index + 1}. ${frame.render()}" }
            .ifEmpty { listOf("No stack frames were parsed.") }
            .joinToString("\n")

        val filesToInspect = resolvedFrames
            .take(MAX_SUMMARY_FRAMES)
            .map(::renderResolvedFrame)
            .ifEmpty { listOf("No source files were found in the parsed frames.") }
            .joinToString("\n")

        return buildString {
            appendLine("Summary")
            appendLine(summary)
            appendLine()
            appendLine("Likely Root Cause")
            appendLine("Ollama analysis was unavailable. Review the parsed exception details and failure path below.")
            appendLine()
            appendLine("Proposed Fix")
            appendLine("Retry with Ollama running and the configured model installed. If the crash is reproducible in code, inspect the top stack frames for invalid state assumptions or missing guards.")
            appendLine()
            appendLine("Regression Test")
            appendLine("See dedicated code panel below.")
            appendLine()
            appendLine("Files to Inspect")
            appendLine(filesToInspect)
            appendLine()
            appendLine("Bug Report Draft")
            append("Reproduced ${parsed.exceptionType ?: "a runtime failure"} from the pasted stack trace. Local parsing succeeded, but AI analysis could not be completed.")
            appendLine()
            appendLine()
            appendLine("Observed Failure Path")
            append(likelyCause)
        }
    }

    private fun renderResolvedFrame(resolvedFrame: ResolvedFrame): String =
        when (resolvedFrame.status) {
            ResolvedFrame.ResolutionStatus.RESOLVED -> buildString {
                append(resolvedFrame.resolvedPath ?: resolvedFrame.frame.fileName ?: "Unknown file")
                resolvedFrame.lineNumber?.let { append(":$it") }
            }

            ResolvedFrame.ResolutionStatus.AMBIGUOUS -> "Unresolved ${resolvedFrame.frame.fileName}: ${resolvedFrame.details}"
            ResolvedFrame.ResolutionStatus.UNRESOLVED ->
                "Unresolved ${resolvedFrame.frame.fileName ?: resolvedFrame.frame.className}: ${resolvedFrame.details}"
        }

    private fun fallbackRegressionTestCode(regressionTestLanguage: RegressionTestLanguage): String =
        when (regressionTestLanguage.fenceTag) {
            "java" -> """
                @Test
                void reproducesCrashPathFromPastedStackTrace() {
                    // Replace this placeholder with the smallest reproducible setup
                    // around the top stack frame shown in the analysis result.
                }
            """.trimIndent()

            else -> """
                @Test
                fun `reproduces crash path from pasted stack trace`() {
                    // Replace this placeholder with the smallest reproducible setup
                    // around the top stack frame shown in the analysis result.
                }
            """.trimIndent()
        }

    private fun com.crash2test.model.StackFrameInfo.render(): String =
        "${className}.${methodName}(${fileName ?: "Unknown Source"}${lineNumber?.let { ":$it" } ?: ""})"

    private fun buildRegressionTestFileName(fenceTag: String, fallbackFileName: String): String =
        when (fenceTag.lowercase()) {
            "java" -> "GeneratedRegressionTest.java"
            "kotlin" -> "GeneratedRegressionTest.kt"
            "python" -> "generated_regression_test.py"
            "javascript" -> "generatedRegressionTest.js"
            "typescript" -> "generatedRegressionTest.ts"
            "go" -> "generated_regression_test.go"
            "ruby" -> "generated_regression_test.rb"
            "php" -> "GeneratedRegressionTest.php"
            "csharp" -> "GeneratedRegressionTest.cs"
            "cpp" -> "generated_regression_test.cpp"
            "c" -> "generated_regression_test.c"
            "rust" -> "generated_regression_test.rs"
            "swift" -> "GeneratedRegressionTest.swift"
            "scala" -> "GeneratedRegressionTest.scala"
            "groovy" -> "GeneratedRegressionTest.groovy"
            else -> fallbackFileName
        }

    companion object {
        private const val MAX_SUMMARY_FRAMES = 5
    }
}
