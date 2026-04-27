package com.crash2test.services

import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceSnippetExtractorTest {
    private val extractor = SourceSnippetExtractor(contextRadius = 1, maxSnippets = 2)

    @Test
    fun `extracts focused snippets from resolved frames`() {
        val tempFile = createTempFile(suffix = ".kt")
        Files.writeString(
            tempFile,
            """
            class Sample {
                fun run() {
                    val state = currentState()
                    check(state != null) { "boom" }
                }
            }
            """.trimIndent(),
        )
        val resolvedFrame = ResolvedFrame(
            frame = StackFrameInfo(
                className = "com.example.Sample",
                methodName = "run",
                fileName = "Sample.kt",
                lineNumber = 4,
            ),
            resolvedPath = "src/main/kotlin/com/example/Sample.kt",
            navigationPath = tempFile.toString(),
            lineNumber = 4,
            status = ResolvedFrame.ResolutionStatus.RESOLVED,
        )

        val snippets = extractor.extract(listOf(resolvedFrame))

        assertEquals(1, snippets.size)
        assertEquals("src/main/kotlin/com/example/Sample.kt", snippets.single().filePath)
        assertEquals(4, snippets.single().focusLineNumber)
        assertEquals(3, snippets.single().startLineNumber)
        assertEquals(5, snippets.single().endLineNumber)
        assertContains(snippets.single().content, "> 4 |")
        assertContains(snippets.single().content, """check(state != null) { "boom" }""")
    }

    @Test
    fun `ignores unresolved or unreadable frames`() {
        val unresolvedFrame = ResolvedFrame(
            frame = StackFrameInfo(
                className = "com.example.Sample",
                methodName = "run",
                fileName = "Sample.kt",
                lineNumber = 4,
            ),
            resolvedPath = null,
            navigationPath = null,
            lineNumber = 4,
            status = ResolvedFrame.ResolutionStatus.UNRESOLVED,
        )

        val snippets = extractor.extract(listOf(unresolvedFrame))

        assertTrue(snippets.isEmpty())
    }
}
