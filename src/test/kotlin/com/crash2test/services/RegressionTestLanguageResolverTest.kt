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

    @Test
    fun `detects common regression test languages from resolved source extension`() {
        val expectations = mapOf(
            "Sample.py" to "Python",
            "Sample.js" to "JavaScript",
            "Sample.ts" to "TypeScript",
            "Sample.go" to "Go",
            "Sample.rb" to "Ruby",
            "Sample.php" to "PHP",
            "Sample.cs" to "C#",
            "Sample.cpp" to "C++",
            "Sample.c" to "C",
            "Sample.rs" to "Rust",
            "Sample.swift" to "Swift",
            "Sample.scala" to "Scala",
            "Sample.groovy" to "Groovy",
        )

        expectations.forEach { (fileName, displayName) ->
            val language = resolver.resolve(
                listOf(
                    ResolvedFrame(
                        frame = StackFrameInfo("com.example.Sample", "run", fileName, 12),
                        resolvedPath = "src/main/$fileName",
                        navigationPath = "/repo/src/main/$fileName",
                        lineNumber = 12,
                        status = ResolvedFrame.ResolutionStatus.RESOLVED,
                    ),
                ),
            )

            assertEquals(displayName, language.displayName)
        }
    }

    @Test
    fun `falls back to unresolved frame file name when no frame is resolved`() {
        val language = resolver.resolve(
            listOf(
                ResolvedFrame(
                    frame = StackFrameInfo("com.example.Sample", "run", "Sample.tsx", 12),
                    resolvedPath = null,
                    navigationPath = null,
                    lineNumber = 12,
                    status = ResolvedFrame.ResolutionStatus.UNRESOLVED,
                ),
            ),
        )

        assertEquals("TypeScript", language.displayName)
        assertEquals("typescript", language.fenceTag)
    }
}
