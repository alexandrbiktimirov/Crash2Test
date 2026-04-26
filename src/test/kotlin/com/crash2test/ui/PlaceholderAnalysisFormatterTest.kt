package com.crash2test.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderAnalysisFormatterTest {
    private val formatter = PlaceholderAnalysisFormatter()

    @Test
    fun `initial state contains placeholder sections and project name`() {
        val state = formatter.initialState("Crash2TestSandbox")

        assertEquals("Paste a stack trace and run placeholder analysis.", state.statusMessage)
        assertTrue(state.canAnalyze)
        assertContains(state.resultText, """Crash2Test is ready for project "Crash2TestSandbox".""")
        assertContains(state.resultText, "Summary")
        assertContains(state.resultText, "Timeline")
        assertContains(state.resultText, "Likely Root Cause")
        assertContains(state.resultText, "Files to Inspect")
        assertContains(state.resultText, "Regression Tests")
        assertContains(state.resultText, "Bug Report Draft")
    }

    @Test
    fun `analyze rejects blank input with clear message`() {
        val state = formatter.analyze("   \n\t  ")

        assertEquals("Enter a stack trace or runtime error before running analysis.", state.statusMessage)
        assertEquals("", state.resultText)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun `analyze builds structured placeholder output from crash text`() {
        val state = formatter.analyze(
            """
            java.lang.IllegalStateException: boom
                at com.example.SampleService.run(SampleService.kt:42)
                at com.example.Main.main(Main.kt:10)
            """.trimIndent(),
        )

        assertEquals("Placeholder analysis complete.", state.statusMessage)
        assertTrue(state.canAnalyze)
        assertContains(state.resultText, "Placeholder analysis captured 3 line(s) of crash text.")
        assertContains(state.resultText, "java.lang.IllegalStateException: boom")
        assertContains(state.resultText, "Parser and AI integration are not implemented yet in this milestone.")
    }

    @Test
    fun `analyze trims preview line to avoid oversized placeholder output`() {
        val longFirstLine = "x".repeat(200)

        val state = formatter.analyze("$longFirstLine\nat com.example.Sample.run(Sample.kt:1)")

        val expectedPreview = "x".repeat(140)
        assertContains(state.resultText, expectedPreview)
        assertFalse(state.resultText.contains("x".repeat(141)))
    }
}
