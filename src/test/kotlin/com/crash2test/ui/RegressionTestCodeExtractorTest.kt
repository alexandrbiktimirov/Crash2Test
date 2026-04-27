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
}
