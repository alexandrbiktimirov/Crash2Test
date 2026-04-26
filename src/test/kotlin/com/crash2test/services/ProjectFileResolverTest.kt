package com.crash2test.services

import com.crash2test.model.StackFrameInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectFileResolverTest {
    @Test
    fun `selects the candidate whose path matches the class package`() {
        val frame = StackFrameInfo(
            className = "com.example.orders.OrderService",
            methodName = "createOrder",
            fileName = "OrderService.java",
            lineNumber = 42,
        )

        val match = ProjectFileResolver.selectBestMatch(
            frame,
            listOf(
                "C:/repo/src/test/java/com/example/orders/OrderService.java",
                "C:/repo/src/main/java/com/example/payments/OrderService.java",
            ),
        )

        assertEquals("C:/repo/src/test/java/com/example/orders/OrderService.java", match)
    }

    @Test
    fun `returns null when multiple candidates match equally well`() {
        val frame = StackFrameInfo(
            className = "com.example.orders.OrderService",
            methodName = "createOrder",
            fileName = "OrderService.java",
            lineNumber = 42,
        )

        val match = ProjectFileResolver.selectBestMatch(
            frame,
            listOf(
                "C:/repo/a/com/example/orders/OrderService.java",
                "C:/repo/b/com/example/orders/OrderService.java",
            ),
        )

        assertNull(match)
    }

    @Test
    fun `returns null when no package-based match exists`() {
        val frame = StackFrameInfo(
            className = "com.example.orders.OrderService",
            methodName = "createOrder",
            fileName = "OrderService.java",
            lineNumber = 42,
        )

        val match = ProjectFileResolver.selectBestMatch(
            frame,
            listOf("C:/repo/src/main/java/com/example/payments/OrderService.java"),
        )

        assertNull(match)
    }

    @Test
    fun `normalizes windows separators before scoring package matches`() {
        val frame = StackFrameInfo(
            className = "com.example.orders.OrderService",
            methodName = "createOrder",
            fileName = "OrderService.java",
            lineNumber = 42,
        )

        val match = ProjectFileResolver.selectBestMatch(
            frame,
            listOf(
                """C:\repo\src\main\java\com\example\orders\OrderService.java""",
                """C:\repo\src\main\java\com\example\payments\OrderService.java""",
            ),
        )

        assertEquals("C:/repo/src/main/java/com/example/orders/OrderService.java", match)
    }
}
