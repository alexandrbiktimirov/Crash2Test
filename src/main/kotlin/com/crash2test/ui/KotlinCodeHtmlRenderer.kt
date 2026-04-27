package com.crash2test.ui

class KotlinCodeHtmlRenderer {
    fun toHtml(code: String): String = buildString {
        append("<html><body style=\"margin:0; background:#171717; color:#f5f5f5;\">")
        append("<pre style=\"margin:0; padding:16px; font-family:'JetBrains Mono',monospace; font-size:12px; white-space:pre-wrap;\">")
        code.lines().forEachIndexed { index, line ->
            if (index > 0) {
                append('\n')
            }
            append(highlightLine(line))
        }
        append("</pre></body></html>")
    }

    private fun highlightLine(line: String): String {
        val commentIndex = line.indexOf("//")
        val codePart = if (commentIndex >= 0) line.substring(0, commentIndex) else line
        val commentPart = if (commentIndex >= 0) line.substring(commentIndex) else ""

        var highlighted = escapeHtml(codePart)
        highlighted = STRING_PATTERN.replace(highlighted) { matchResult ->
            """<span style="color:#7ee787;">${matchResult.value}</span>"""
        }
        highlighted = ANNOTATION_PATTERN.replace(highlighted) { matchResult ->
            """<span style="color:#ffd580;">${matchResult.value}</span>"""
        }
        highlighted = KEYWORD_PATTERN.replace(highlighted) { matchResult ->
            """<span style="color:#c792ea;">${matchResult.value}</span>"""
        }

        if (commentPart.isNotEmpty()) {
            highlighted += """<span style="color:#8b949e;">${escapeHtml(commentPart)}</span>"""
        }

        return highlighted
    }

    private fun escapeHtml(text: String): String = buildString {
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

    companion object {
        private val STRING_PATTERN = Regex(""""([^"\\\\]|\\\\.)*"""")
        private val ANNOTATION_PATTERN = Regex("""@\w+""")
        private val KEYWORD_PATTERN = Regex(
            """\b(class|fun|val|var|object|if|else|when|return|try|catch|throw|for|while|null|true|false|private|public|internal|protected|override|data|sealed|interface|import|package)\b""",
        )
    }
}
