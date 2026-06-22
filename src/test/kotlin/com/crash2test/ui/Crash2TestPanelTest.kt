package com.crash2test.ui

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.RegressionTestLanguage
import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import com.crash2test.services.CrashAnalyzer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.junit.Test
import java.util.function.BooleanSupplier
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Crash2TestPanelTest : LightPlatform4TestCase() {
    @Test
    fun rendersInitialState() {
        val panel = Crash2TestPanel(project, analysisService = failingAnalyzer())
        try {
            assertEquals(
                "Paste a stack trace or runtime error, then press Analyze.",
                panel.privateField<JBLabel>("statusLabel").text,
            )
            assertTrue(panel.privateField<JButton>("analyzeButton").isEnabled)
            assertFalse(panel.privateField<JBScrollPane>("resultScrollPane").isVisible)
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun runsAnalysisAndRendersSuccessfulResult() {
        val frame = StackFrameInfo(
            className = "com.example.OrderService",
            methodName = "create",
            fileName = "OrderService.kt",
            lineNumber = 42,
        )
        val resolvedFrame = ResolvedFrame(
            frame = frame,
            resolvedPath = "src/main/kotlin/com/example/OrderService.kt",
            navigationPath = "/tmp/OrderService.kt",
            lineNumber = 42,
            status = ResolvedFrame.ResolutionStatus.RESOLVED,
        )
        val analyzer = CrashAnalyzer { rawInput, onChunk ->
            assertContains(rawInput, "IllegalStateException")
            onChunk("Summary\n")
            CrashAnalysisResult.Success(
                parsedStackTrace = ParsedStackTrace("java.lang.IllegalStateException", "boom", listOf(frame)),
                resolvedFrames = listOf(resolvedFrame),
                regressionTestLanguage = RegressionTestLanguage("Kotlin", "kotlin", "GeneratedRegressionTest.kt"),
                prompt = "prompt",
                analysisText = """
                    Summary
                    AI result

                    Regression Test
                    ```kotlin
                    @Test
                    fun works() {
                        assertTrue(true)
                    }
                    ```
                """.trimIndent(),
            )
        }
        val panel = Crash2TestPanel(project, analysisService = analyzer)
        try {
            val inputArea = panel.privateField<JBTextArea>("inputArea")
            val analyzeButton = panel.privateField<JButton>("analyzeButton")
            val statusLabel = panel.privateField<JBLabel>("statusLabel")

            runOnEdt {
                inputArea.text = "java.lang.IllegalStateException: boom"
                analyzeButton.doClick()
            }
            PlatformTestUtil.waitWithEventsDispatching(
                "analysis to complete",
                BooleanSupplier { statusLabel.text == "Analysis complete." },
                5000,
            )

            val resultArea = panel.privateField<JEditorPane>("resultArea")
            val frameModel = panel.privateField<DefaultListModel<ResolvedFrame>>("resolvedFrameModel")
            val frameList = panel.privateField<JBList<ResolvedFrame>>("resolvedFramesList")

            assertContains(resultArea.text, "AI result")
            assertContains(panel.privateField<String>("currentRegressionTestCode"), "fun works()")
            assertEquals(1, frameModel.size())
            assertEquals(resolvedFrame, frameList.model.getElementAt(0))
            assertTrue(analyzeButton.isEnabled)
        } finally {
            panel.dispose()
        }
    }

    private fun failingAnalyzer() = CrashAnalyzer { _, _ -> error("Analyzer should not be called") }

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeAndWait(action)
        }
    }

    private inline fun <reified T> Crash2TestPanel.privateField(name: String): T {
        val field = Crash2TestPanel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }
}
