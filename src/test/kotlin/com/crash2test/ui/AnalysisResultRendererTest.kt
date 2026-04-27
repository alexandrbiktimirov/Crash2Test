package com.crash2test.ui

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.RegressionTestLanguage
import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisResultRendererTest {
    private val renderer = AnalysisResultRenderer()

    @Test
    fun `renders successful analysis response`() {
        val result = CrashAnalysisResult.Success(
            parsedStackTrace = ParsedStackTrace("java.lang.IllegalStateException", "boom", emptyList()),
            resolvedFrames = emptyList(),
            regressionTestLanguage = RegressionTestLanguage("Java", "java", "GeneratedRegressionTest.java"),
            prompt = "prompt",
            analysisText = """
                Summary
                AI output

                Regression Test
                ```java
                @Test
                void works() {
                    assertTrue(true);
                }
                ```
            """.trimIndent(),
        )

        val state = renderer.render(result)

        assertEquals("Analysis complete.", state.statusMessage)
        assertContains(state.resultText, "See dedicated code panel below.")
        assertEquals(
            """
            @Test
            void works() {
                assertTrue(true);
            }
            """.trimIndent(),
            state.regressionTestCode,
        )
        assertEquals("java", state.regressionTestFenceTag)
        assertEquals("GeneratedRegressionTest.java", state.regressionTestFileName)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun `renders parsed fallback details when ollama fails`() {
        val frame = StackFrameInfo(
            className = "com.example.orders.OrderService",
            methodName = "createOrder",
            fileName = "OrderService.kt",
            lineNumber = 42,
        )
        val result = CrashAnalysisResult.Failure(
            message = "Could not connect to Ollama at http://localhost:11434. Make sure Ollama is running.",
            parsedStackTrace = ParsedStackTrace(
                exceptionType = "java.lang.IllegalStateException",
                exceptionMessage = "boom",
                frames = listOf(frame),
            ),
            resolvedFrames = listOf(
                ResolvedFrame(
                    frame = frame,
                    resolvedPath = "src/main/kotlin/com/example/orders/OrderService.kt",
                    navigationPath = "C:/repo/src/main/kotlin/com/example/orders/OrderService.kt",
                    lineNumber = 42,
                    status = ResolvedFrame.ResolutionStatus.RESOLVED,
                ),
            ),
            regressionTestLanguage = RegressionTestLanguage("Kotlin", "kotlin", "GeneratedRegressionTest.kt"),
        )

        val state = renderer.render(result)

        assertEquals("Could not connect to Ollama at http://localhost:11434. Make sure Ollama is running.", state.statusMessage)
        assertContains(state.resultText, "java.lang.IllegalStateException: boom (1 frame(s))")
        assertContains(state.resultText, "Observed Failure Path")
        assertContains(state.resultText, "1. com.example.orders.OrderService.createOrder(OrderService.kt:42)")
        assertContains(state.resultText, "Proposed Fix")
        assertContains(state.resultText, "Regression Test")
        assertContains(state.resultText, "See dedicated code panel below.")
        assertContains(state.regressionTestCode ?: "", "fun `reproduces crash path from pasted stack trace`()")
        assertEquals("kotlin", state.regressionTestFenceTag)
        assertEquals("GeneratedRegressionTest.kt", state.regressionTestFileName)
        assertContains(state.resultText, "src/main/kotlin/com/example/orders/OrderService.kt:42")
        assertEquals(1, state.clickableFrames.size)
        assertTrue(state.canAnalyze)
    }
}
