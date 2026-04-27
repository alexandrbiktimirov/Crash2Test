package com.crash2test.ui

class MarkdownToHtmlRenderer {
    fun toHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val html = StringBuilder()
        html.append("<html><body style=\"font-family:sans-serif; font-size:12px; margin:8px;\">")

        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()

            if (trimmed.isBlank()) {
                index++
                continue
            }

            if (isHeading(trimmed)) {
                html.append("<h3 style=\"margin:12px 0 6px 0;\">")
                html.append(formatInline(trimmed.removeSuffix(":")))
                html.append("</h3>")
                index++
                continue
            }

            if (trimmed.matches(ORDERED_LIST_PATTERN)) {
                html.append("<ol style=\"margin:6px 0 10px 20px;\">")
                while (index < lines.size && lines[index].trim().matches(ORDERED_LIST_PATTERN)) {
                    val item = lines[index].trim().replaceFirst(Regex("""^\d+\.\s+"""), "")
                    html.append("<li>").append(formatInline(item)).append("</li>")
                    index++
                }
                html.append("</ol>")
                continue
            }

            if (trimmed.matches(UNORDERED_LIST_PATTERN)) {
                html.append("<ul style=\"margin:6px 0 10px 20px;\">")
                while (index < lines.size && lines[index].trim().matches(UNORDERED_LIST_PATTERN)) {
                    val item = lines[index].trim().removePrefix("- ").removePrefix("* ")
                    html.append("<li>").append(formatInline(item)).append("</li>")
                    index++
                }
                html.append("</ul>")
                continue
            }

            val paragraphLines = mutableListOf<String>()
            while (index < lines.size) {
                val candidate = lines[index].trim()
                if (candidate.isBlank() || isHeading(candidate) ||
                    candidate.matches(ORDERED_LIST_PATTERN) ||
                    candidate.matches(UNORDERED_LIST_PATTERN)
                ) {
                    break
                }
                paragraphLines += candidate
                index++
            }

            html.append("<p style=\"margin:6px 0;\">")
            html.append(formatInline(paragraphLines.joinToString(" ")))
            html.append("</p>")
        }

        html.append("</body></html>")
        return html.toString()
    }

    private fun isHeading(line: String): Boolean = line.removeSuffix(":") in headings

    private fun formatInline(text: String): String {
        var escaped = buildString {
            text.forEach { character ->
                append(
                    when (character) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        else -> character
                    },
                )
            }
        }

        escaped = escaped.replace(Regex("""`([^`]+)`"""), "<code>$1</code>")
        escaped = escaped.replace(Regex("""\*\*([^*]+)\*\*"""), "<b>$1</b>")
        escaped = escaped.replace(Regex("""\*([^*]+)\*"""), "<i>$1</i>")
        return escaped
    }

    companion object {
        private val ORDERED_LIST_PATTERN = Regex("""^\d+\.\s+.+$""")
        private val UNORDERED_LIST_PATTERN = Regex("""^[-*]\s+.+$""")
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
