package com.crash2test.ui

import kotlin.test.Test
import kotlin.test.assertContains

class MarkdownToHtmlRendererTest {
    private val renderer = MarkdownToHtmlRenderer()

    @Test
    fun `renders headings inline code and lists into html`() {
        val html = renderer.toHtml(
            """
            Summary:
            The crash occurred in the `Crash2TestPanel` class.

            Files to Inspect:
            1. `Crash2TestPanel.kt` (line 142)
            2. `CrashAnalysisService.kt` (line 40)

            Regression Tests:
            - Test `startAnalysis()`
            - Test `analyzeStreaming()`
            """.trimIndent(),
        )

        assertContains(html, "<h3")
        assertContains(html, "Summary")
        assertContains(html, "<code>Crash2TestPanel</code>")
        assertContains(html, "<ol")
        assertContains(html, "<li><code>Crash2TestPanel.kt</code> (line 142)</li>")
        assertContains(html, "<ul")
        assertContains(html, "<li>Test <code>startAnalysis()</code></li>")
    }
}
