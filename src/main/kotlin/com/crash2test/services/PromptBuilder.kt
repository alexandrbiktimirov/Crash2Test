package com.crash2test.services

import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.RegressionTestLanguage
import com.crash2test.model.ResolvedCodeSnippet
import com.crash2test.model.ResolvedFrame

class PromptBuilder {
    fun build(
        parsed: ParsedStackTrace,
        resolvedFrames: List<ResolvedFrame>,
        codeSnippets: List<ResolvedCodeSnippet> = emptyList(),
        regressionTestLanguage: RegressionTestLanguage = RegressionTestLanguage(
            displayName = "Kotlin",
            fenceTag = "kotlin",
            suggestedFileName = "GeneratedRegressionTest.kt",
        ),
    ): String = buildString {
        appendLine("You are analyzing a Java or Kotlin crash inside an IntelliJ IDEA plugin.")
        appendLine("Use only the crash details below.")
        appendLine("Do not invent files, classes, or stack frames that are not present.")
        appendLine("Keep the response concise and practical.")
        appendLine("Do not ask for more information.")
        appendLine("If the cause is uncertain, say it is likely rather than certain.")
        appendLine("Propose a concrete fix when possible.")
        appendLine("For Regression Test, do not default to a unit test.")
        appendLine("Prefer a behavioral or integration-style regression test that reproduces the user-visible failure path.")
        appendLine("If an automated regression test is appropriate, provide a single copyable ${regressionTestLanguage.displayName} regression test snippet inside a fenced ```${regressionTestLanguage.fenceTag} code block```.")
        appendLine("If code would be misleading without missing setup, describe the regression scenario and test steps instead of inventing a unit test.")
        appendLine("Use the provided code snippets when they are available.")
        appendLine("Do not assume code exists beyond the provided snippets.")
        appendLine()
        appendLine("Return exactly these sections:")
        appendLine("Summary")
        appendLine("Likely Root Cause")
        appendLine("Proposed Fix")
        appendLine("Regression Test")
        appendLine("Files to Inspect")
        appendLine("Bug Report Draft")
        appendLine()
        appendLine("Exception Type: ${parsed.exceptionType ?: "Unknown"}")
        appendLine("Exception Message: ${parsed.exceptionMessage ?: "Not provided"}")
        appendLine()
        appendLine("Parsed Stack Frames:")
        if (parsed.frames.isEmpty()) {
            appendLine("- None")
        } else {
            parsed.frames.forEachIndexed { index, frame ->
                val source = buildString {
                    append(frame.fileName ?: "Unknown Source")
                    frame.lineNumber?.let { append(":$it") }
                }
                appendLine("${index + 1}. ${frame.className}.${frame.methodName}($source)")
            }
        }
        appendLine()
        appendLine("Resolved Project Files:")
        if (resolvedFrames.isEmpty()) {
            appendLine("- None")
        } else {
            resolvedFrames.forEach { resolvedFrame ->
                val sourceLabel = resolvedFrame.resolvedPath
                    ?: resolvedFrame.frame.fileName
                    ?: resolvedFrame.frame.className
                val lineSuffix = resolvedFrame.lineNumber?.let { ":$it" }.orEmpty()
                val detailsSuffix = resolvedFrame.details?.let { " [$it]" }.orEmpty()
                appendLine("- ${resolvedFrame.status}: $sourceLabel$lineSuffix$detailsSuffix")
            }
        }

        appendLine()
        appendLine("Relevant Code Snippets:")
        if (codeSnippets.isEmpty()) {
            appendLine("- None")
        } else {
            codeSnippets.forEachIndexed { index, snippet ->
                appendLine("Snippet ${index + 1}: ${snippet.filePath}:${snippet.focusLineNumber} (lines ${snippet.startLineNumber}-${snippet.endLineNumber})")
                appendLine("```kotlin")
                appendLine(snippet.content)
                appendLine("```")
            }
        }
    }
}
