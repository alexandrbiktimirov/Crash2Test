package com.crash2test.services

import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectFileResolverPlatformTest : LightPlatformCodeInsightFixture4TestCase() {
    @Test
    fun resolvesSingleMatchingProjectFile() {
        myFixture.addFileToProject(
            "src/main/kotlin/com/example/orders/OrderService.kt",
            "package com.example.orders\nclass OrderService",
        )

        val result = ProjectFileResolver(project).resolve(
            parsedTrace(
                StackFrameInfo(
                    className = "com.example.orders.OrderService",
                    methodName = "createOrder",
                    fileName = "OrderService.kt",
                    lineNumber = 12,
                ),
            ),
        ).single()

        assertEquals(ResolvedFrame.ResolutionStatus.RESOLVED, result.status)
        assertTrue(result.resolvedPath?.endsWith("src/main/kotlin/com/example/orders/OrderService.kt") == true)
        assertTrue(result.navigationPath?.endsWith("src/main/kotlin/com/example/orders/OrderService.kt") == true)
        assertEquals(12, result.lineNumber)
    }

    @Test
    fun selectsPackagePathWhenMultipleFilesShareName() {
        myFixture.addFileToProject(
            "src/main/kotlin/com/example/orders/OrderService.kt",
            "package com.example.orders\nclass OrderService",
        )
        myFixture.addFileToProject(
            "src/main/kotlin/com/example/payments/OrderService.kt",
            "package com.example.payments\nclass OrderService",
        )

        val result = ProjectFileResolver(project).resolve(
            parsedTrace(
                StackFrameInfo(
                    className = "com.example.orders.OrderService",
                    methodName = "createOrder",
                    fileName = "OrderService.kt",
                    lineNumber = 12,
                ),
            ),
        ).single()

        assertEquals(ResolvedFrame.ResolutionStatus.RESOLVED, result.status)
        assertTrue(result.resolvedPath?.endsWith("src/main/kotlin/com/example/orders/OrderService.kt") == true)
        assertEquals("Matched by package path.", result.details)
    }

    @Test
    fun marksFrameAmbiguousWhenMultipleCandidatesCannotBeRanked() {
        myFixture.addFileToProject("src/main/kotlin/a/OrderService.kt", "class OrderService")
        myFixture.addFileToProject("src/main/kotlin/b/OrderService.kt", "class OrderService")

        val result = ProjectFileResolver(project).resolve(
            parsedTrace(
                StackFrameInfo(
                    className = "com.example.orders.OrderService",
                    methodName = "createOrder",
                    fileName = "OrderService.kt",
                    lineNumber = 12,
                ),
            ),
        ).single()

        assertEquals(ResolvedFrame.ResolutionStatus.AMBIGUOUS, result.status)
        assertNotNull(result.details)
        assertContains(result.details ?: "", "Multiple matching project files were found")
    }

    @Test
    fun marksFrameUnresolvedWhenFileIsMissing() {
        val result = ProjectFileResolver(project).resolve(
            parsedTrace(
                StackFrameInfo(
                    className = "com.example.orders.OrderService",
                    methodName = "createOrder",
                    fileName = "OrderService.kt",
                    lineNumber = 12,
                ),
            ),
        ).single()

        assertEquals(ResolvedFrame.ResolutionStatus.UNRESOLVED, result.status)
        assertEquals("No matching project file was found.", result.details)
    }

    private fun parsedTrace(frame: StackFrameInfo) = ParsedStackTrace(
        exceptionType = "java.lang.IllegalStateException",
        exceptionMessage = "boom",
        frames = listOf(frame),
    )
}
