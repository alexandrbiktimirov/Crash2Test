package com.crash2test.ui

class RegressionTestCodeExtractor {
    fun extract(markdown: String): ExtractionResult {
        val normalized = markdown.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        val sectionStart = lines.indexOfFirst { isRegressionHeading(it.trim()) }
        if (sectionStart < 0) {
            return ExtractionResult(normalized, null)
        }

        val sectionEnd = (sectionStart + 1 until lines.size)
            .firstOrNull { isHeading(lines[it].trim()) }
            ?: lines.size

        val bodyLines = lines.subList(sectionStart + 1, sectionEnd)
        val body = bodyLines.joinToString(separator = "\n")
        val match = CODE_BLOCK_PATTERN.find(body) ?: return ExtractionResult(normalized, null)
        val code = match.groupValues[2].trim('\n', '\r').trimEnd()
        val fenceTag = match.groupValues[1].ifBlank { null }
        if (code.isBlank()) {
            return ExtractionResult(normalized, null)
        }

        val remainingBody = body.removeRange(match.range).trim()
        val replacementBody = if (remainingBody.isBlank()) {
            "See dedicated code panel below."
        } else {
            "$remainingBody\n\nSee dedicated code panel below."
        }

        val rebuiltLines = mutableListOf<String>().apply {
            addAll(lines.subList(0, sectionStart + 1))
            addAll(replacementBody.split('\n'))
            addAll(lines.subList(sectionEnd, lines.size))
        }

        return ExtractionResult(
            markdown = rebuiltLines.joinToString(separator = "\n").trim(),
            regressionTestCode = RegressionTestCodeBlock(
                code = code,
                fenceTag = fenceTag,
            ),
        )
    }

    private fun isRegressionHeading(line: String): Boolean = line == "Regression Test" || line == "Regression Test:"

    private fun isHeading(line: String): Boolean = line.removeSuffix(":") in headings

    data class ExtractionResult(
        val markdown: String,
        val regressionTestCode: RegressionTestCodeBlock?,
    )

    data class RegressionTestCodeBlock(
        val code: String,
        val fenceTag: String?,
    )

    companion object {
        private val CODE_BLOCK_PATTERN = Regex("""```([A-Za-z0-9_+#-]*)\s*\n(.*?)\n```""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val headings = setOf(
            "Summary",
            "Likely Root Cause",
            "Proposed Fix",
            "Regression Test",
            "Files to Inspect",
            "Bug Report Draft",
            "Observed Failure Path",
            "Description",
            "Steps to Reproduce",
            "Impact",
            "Additional Information",
            "Possible Mitigations",
            "Title",
        )
    }
}
