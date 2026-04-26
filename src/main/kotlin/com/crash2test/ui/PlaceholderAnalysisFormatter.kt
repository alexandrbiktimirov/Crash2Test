package com.crash2test.ui

import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.ResolvedFrame
import com.crash2test.services.ProjectFileResolver
import com.crash2test.services.StackTraceParser

class PlaceholderAnalysisFormatter(
    private val projectFileResolver: ProjectFileResolver? = null,
    private val stackTraceParser: StackTraceParser = StackTraceParser(),
) {
    fun initialState(projectName: String): Crash2TestViewState = Crash2TestViewState(
        statusMessage = "Paste a stack trace or runtime error to parse it.",
        resultText = """
            Summary
            Crash2Test is ready for project "$projectName".

            Timeline
            Paste a Java or Kotlin stack trace to extract exception details and frames.

            Likely Root Cause
            Not analyzed yet.

            Files to Inspect
            No parsed stack frames yet.

            Regression Tests
            Parser milestone only. Test suggestions will be added after AI integration.

            Bug Report Draft
            Add crash details to build a draft from parsed results in later milestones.
        """.trimIndent(),
        canAnalyze = true,
    )

    fun analyze(rawInput: String): Crash2TestViewState {
        val normalizedInput = rawInput.trim()
        if (normalizedInput.isEmpty()) {
            return Crash2TestViewState(
                statusMessage = "Enter a stack trace or runtime error before running analysis.",
                resultText = "",
                canAnalyze = true,
            )
        }

        val parsed = stackTraceParser.parse(normalizedInput)
        if (parsed.exceptionType == null && parsed.frames.isEmpty()) {
            return Crash2TestViewState(
                statusMessage = "Could not recognize a Java or Kotlin stack trace.",
                resultText = "",
                canAnalyze = true,
            )
        }

        val resolvedFrames = projectFileResolver?.resolve(parsed).orEmpty()

        return Crash2TestViewState(
            statusMessage = "Stack trace parsed successfully.",
            resultText = formatParsedResult(parsed, resolvedFrames),
            canAnalyze = true,
            clickableFrames = resolvedFrames.filter { it.status == ResolvedFrame.ResolutionStatus.RESOLVED },
        )
    }

    private fun formatParsedResult(parsed: ParsedStackTrace, resolvedFrames: List<ResolvedFrame>): String {
        val summary = buildString {
            append(parsed.exceptionType ?: "Exception type not detected")
            parsed.exceptionMessage?.let { append(": $it") }
            append(" (${parsed.frames.size} frame(s))")
        }

        val timeline = parsed.frames
            .take(5)
            .mapIndexed { index, frame ->
                "${index + 1}. ${frame.className}.${frame.methodName}(${frame.fileName ?: "Unknown Source"}${frame.lineNumber?.let { ":$it" } ?: ""})"
            }
            .ifEmpty { listOf("No stack frames were parsed.") }
            .joinToString("\n")

        val filesToInspect = resolvedFrames
            .take(5)
            .map { resolvedFrame ->
                when (resolvedFrame.status) {
                    ResolvedFrame.ResolutionStatus.RESOLVED -> buildString {
                        append(resolvedFrame.resolvedPath ?: resolvedFrame.frame.fileName ?: "Unknown file")
                        resolvedFrame.lineNumber?.let { append(":$it") }
                    }

                    ResolvedFrame.ResolutionStatus.AMBIGUOUS -> "Unresolved ${resolvedFrame.frame.fileName}: ${resolvedFrame.details}"
                    ResolvedFrame.ResolutionStatus.UNRESOLVED -> "Unresolved ${resolvedFrame.frame.fileName ?: resolvedFrame.frame.className}: ${resolvedFrame.details}"
                }
            }
            .ifEmpty { listOf("No source files were found in the parsed frames.") }
            .joinToString("\n")

        return buildString {
            appendLine("Summary")
            appendLine(summary)
            appendLine()
            appendLine("Timeline")
            appendLine(timeline)
            appendLine()
            appendLine("Likely Root Cause")
            appendLine(parsed.exceptionMessage ?: "The parser extracted stack frames but no exception message was provided.")
            appendLine()
            appendLine("Files to Inspect")
            appendLine(filesToInspect)
            appendLine()
            appendLine("Regression Tests")
            appendLine("Add parser-focused tests around the failure path shown by the parsed frames.")
            appendLine()
            appendLine("Bug Report Draft")
            append("Reproduced ${parsed.exceptionType ?: "a runtime failure"} from the pasted stack trace. Parsed ${parsed.frames.size} frame(s) for follow-up investigation.")
        }
    }
}
