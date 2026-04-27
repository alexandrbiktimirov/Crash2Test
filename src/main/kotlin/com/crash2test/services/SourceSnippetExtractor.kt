package com.crash2test.services

import com.crash2test.model.ResolvedCodeSnippet
import com.crash2test.model.ResolvedFrame
import java.nio.file.Files
import java.nio.file.Path

class SourceSnippetExtractor(
    private val contextRadius: Int = DEFAULT_CONTEXT_RADIUS,
    private val maxSnippets: Int = DEFAULT_MAX_SNIPPETS,
) {
    fun extract(resolvedFrames: List<ResolvedFrame>): List<ResolvedCodeSnippet> =
        resolvedFrames.asSequence()
            .filter { it.status == ResolvedFrame.ResolutionStatus.RESOLVED }
            .filter { it.navigationPath != null && it.lineNumber != null }
            .distinctBy { "${it.navigationPath}:${it.lineNumber}" }
            .take(maxSnippets)
            .mapNotNull(::extractSnippet)
            .toList()

    private fun extractSnippet(resolvedFrame: ResolvedFrame): ResolvedCodeSnippet? {
        val path = resolvedFrame.navigationPath?.let(Path::of) ?: return null
        val focusLineNumber = resolvedFrame.lineNumber?.takeIf { it > 0 } ?: return null
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return null
        }

        val lines = runCatching { Files.readAllLines(path) }.getOrNull() ?: return null
        if (lines.isEmpty()) {
            return null
        }

        val startLineNumber = (focusLineNumber - contextRadius).coerceAtLeast(1)
        val endLineNumber = (focusLineNumber + contextRadius).coerceAtMost(lines.size)
        val content = formatSnippet(
            lines = lines,
            startLineNumber = startLineNumber,
            endLineNumber = endLineNumber,
            focusLineNumber = focusLineNumber,
        )

        return ResolvedCodeSnippet(
            filePath = resolvedFrame.resolvedPath ?: path.fileName.toString(),
            focusLineNumber = focusLineNumber,
            startLineNumber = startLineNumber,
            endLineNumber = endLineNumber,
            content = content,
        )
    }

    private fun formatSnippet(
        lines: List<String>,
        startLineNumber: Int,
        endLineNumber: Int,
        focusLineNumber: Int,
    ): String {
        val lineNumberWidth = endLineNumber.toString().length
        return (startLineNumber..endLineNumber).joinToString(separator = "\n") { lineNumber ->
            val marker = if (lineNumber == focusLineNumber) ">" else " "
            val paddedLineNumber = lineNumber.toString().padStart(lineNumberWidth, ' ')
            val lineContent = lines[lineNumber - 1]
            "$marker $paddedLineNumber | $lineContent"
        }
    }

    companion object {
        private const val DEFAULT_CONTEXT_RADIUS = 8
        private const val DEFAULT_MAX_SNIPPETS = 3
    }
}
