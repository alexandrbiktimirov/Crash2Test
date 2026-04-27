package com.crash2test.services

import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.RegressionTestLanguage
import com.crash2test.model.ResolvedCodeSnippet
import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import kotlin.test.Test
import kotlin.test.assertContains

class PromptBuilderTest {
    private val promptBuilder = PromptBuilder()

    @Test
    fun `builds structured prompt from parsed crash and resolved files`() {
        val parsed = ParsedStackTrace(
            exceptionType = "java.lang.IllegalStateException",
            exceptionMessage = "boom",
            frames = listOf(
                StackFrameInfo(
                    className = "com.example.orders.OrderService",
                    methodName = "createOrder",
                    fileName = "OrderService.kt",
                    lineNumber = 42,
                ),
            ),
        )
        val resolvedFrames = listOf(
            ResolvedFrame(
                frame = parsed.frames.single(),
                resolvedPath = "src/main/kotlin/com/example/orders/OrderService.kt",
                navigationPath = "C:/repo/src/main/kotlin/com/example/orders/OrderService.kt",
                lineNumber = 42,
                status = ResolvedFrame.ResolutionStatus.RESOLVED,
                details = "Matched by package path.",
            ),
        )
        val codeSnippets = listOf(
            ResolvedCodeSnippet(
                filePath = "src/main/kotlin/com/example/orders/OrderService.kt",
                focusLineNumber = 42,
                startLineNumber = 38,
                endLineNumber = 44,
                content = """
                      38 | fun createOrder() {
                      39 |     val state = currentState
                    > 42 |     check(state != null) { "boom" }
                      43 | }
                """.trimIndent(),
            ),
        )
        val regressionTestLanguage = RegressionTestLanguage(
            displayName = "Java",
            fenceTag = "java",
            suggestedFileName = "GeneratedRegressionTest.java",
        )

        val prompt = promptBuilder.build(parsed, resolvedFrames, codeSnippets, regressionTestLanguage)

        assertContains(prompt, "Return exactly these sections:")
        assertContains(prompt, "Summary")
        assertContains(prompt, "Likely Root Cause")
        assertContains(prompt, "Proposed Fix")
        assertContains(prompt, "Regression Test")
        assertContains(prompt, "Files to Inspect")
        assertContains(prompt, "Bug Report Draft")
        assertContains(prompt, "For Regression Test, do not default to a unit test.")
        assertContains(prompt, "Prefer a behavioral or integration-style regression test that reproduces the user-visible failure path.")
        assertContains(prompt, "If an automated regression test is appropriate, provide a single copyable Java regression test snippet inside a fenced ```java code block```.")
        assertContains(prompt, "If code would be misleading without missing setup, describe the regression scenario and test steps instead of inventing a unit test.")
        assertContains(prompt, "Use the provided code snippets when they are available.")
        assertContains(prompt, "Exception Type: java.lang.IllegalStateException")
        assertContains(prompt, "Exception Message: boom")
        assertContains(prompt, "1. com.example.orders.OrderService.createOrder(OrderService.kt:42)")
        assertContains(prompt, "- RESOLVED: src/main/kotlin/com/example/orders/OrderService.kt:42 [Matched by package path.]")
        assertContains(prompt, "Relevant Code Snippets:")
        assertContains(prompt, "Snippet 1: src/main/kotlin/com/example/orders/OrderService.kt:42 (lines 38-44)")
        assertContains(prompt, "```kotlin")
        assertContains(prompt, """check(state != null) { "boom" }""")
    }
}
