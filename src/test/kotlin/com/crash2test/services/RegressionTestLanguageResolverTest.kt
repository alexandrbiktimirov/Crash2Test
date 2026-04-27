package com.crash2test.services

import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class RegressionTestLanguageResolverTest {
    private val resolver = RegressionTestLanguageResolver()

    @Test
    fun `prefers java when top resolved frame is java`() {
        val language = resolver.resolve(
            listOf(
                ResolvedFrame(
                    frame = StackFrameInfo("com.example.Sample", "run", "Sample.java", 12),
                    resolvedPath = "src/main/java/com/example/Sample.java",
                    navigationPath = "C:/repo/src/main/java/com/example/Sample.java",
                    lineNumber = 12,
                    status = ResolvedFrame.ResolutionStatus.RESOLVED,
                ),
            ),
        )

        assertEquals("Java", language.displayName)
        assertEquals("java", language.fenceTag)
        assertEquals("GeneratedRegressionTest.java", language.suggestedFileName)
    }

    @Test
    fun `defaults to kotlin when no resolved language is available`() {
        val language = resolver.resolve(emptyList())

        assertEquals("Kotlin", language.displayName)
        assertEquals("kotlin", language.fenceTag)
    }
}
