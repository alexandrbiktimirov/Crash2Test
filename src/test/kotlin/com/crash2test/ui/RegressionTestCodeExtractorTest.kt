package com.crash2test.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegressionTestCodeExtractorTest {
    private val extractor = RegressionTestCodeExtractor()

    @Test
    fun `extracts fenced regression test code and leaves narrative text`() {
        val result = extractor.extract(
            """
            Summary
            Crash summary.

            Regression Test
            ```kotlin
            @Test
            fun `works`() {
                assertTrue(true)
            }
            ```

            Files to Inspect
            src/main/kotlin/Sample.kt
            """.trimIndent(),
        )

        assertContains(result.markdown, "Regression Test")
        assertContains(result.markdown, "See dedicated code panel below.")
        assertContains(result.markdown, "Files to Inspect")
        assertEquals(
            """
            @Test
            fun `works`() {
                assertTrue(true)
            }
            """.trimIndent(),
            result.regressionTestCode?.code,
        )
        assertEquals("kotlin", result.regressionTestCode?.fenceTag)
    }

    @Test
    fun `returns null code when no fenced block exists`() {
        val result = extractor.extract(
            """
            Summary
            Crash summary.

            Regression Test
            Write a test for the failure path.
            """.trimIndent(),
        )

        assertNull(result.regressionTestCode)
    }

    @Test
    fun `extracts code from markdown heading and stops at plural heading variant`() {
        val result = extractor.extract(
            """
            ## Regression Tests:
            Keep this context.

            ```java
            @Test
            void reproducesCrash() {
            }
            ```

            ## Files to Inspect
            OrderService.java
            """.trimIndent(),
        )

        assertContains(result.markdown, "Keep this context.")
        assertContains(result.markdown, "See dedicated code panel below.")
        assertContains(result.markdown, "## Files to Inspect")
        assertEquals(
            """
            @Test
            void reproducesCrash() {
            }
            """.trimIndent(),
            result.regressionTestCode?.code,
        )
        assertEquals("java", result.regressionTestCode?.fenceTag)
    }
}
