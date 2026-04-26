package com.crash2test.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderAnalysisFormatterTest {
    private val formatter = PlaceholderAnalysisFormatter()

    @Test
    fun `initial state contains placeholder sections and project name`() {
        val state = formatter.initialState("Crash2TestSandbox")

        assertEquals("Paste a stack trace or runtime error to parse it.", state.statusMessage)
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

        assertEquals("Stack trace parsed successfully.", state.statusMessage)
        assertTrue(state.canAnalyze)
        assertContains(state.resultText, "java.lang.IllegalStateException: boom (2 frame(s))")
        assertContains(state.resultText, "1. com.example.SampleService.run(SampleService.kt:42)")
        assertContains(state.resultText, "SampleService.kt:42")
    }

    @Test
    fun `analyze rejects invalid stack trace input`() {
        val state = formatter.analyze("x".repeat(200))

        assertEquals("Could not recognize a Java or Kotlin stack trace.", state.statusMessage)
        assertEquals("", state.resultText)
        assertTrue(state.canAnalyze)
    }
}
