package com.crash2test.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StackTraceParserTest {
    private val parser = StackTraceParser()

    @Test
    fun `parses java exception header and stack frames`() {
        val parsed = parser.parse(
            """
            java.lang.IllegalStateException: boom
                at com.example.orders.OrderService.createOrder(OrderService.java:42)
                at com.example.Main.main(Main.java:10)
            """.trimIndent(),
        )

        assertEquals("java.lang.IllegalStateException", parsed.exceptionType)
        assertEquals("boom", parsed.exceptionMessage)
        assertEquals(2, parsed.frames.size)
        assertEquals("com.example.orders.OrderService", parsed.frames[0].className)
        assertEquals("createOrder", parsed.frames[0].methodName)
        assertEquals("OrderService.java", parsed.frames[0].fileName)
        assertEquals(42, parsed.frames[0].lineNumber)
    }

    @Test
    fun `parses kotlin stack trace with thread prefix and partial sources`() {
        val parsed = parser.parse(
            """
            Exception in thread "main" kotlin.NullPointerException: name was null
                at com.example.FeatureService.run(FeatureService.kt:12)
                at com.example.FeatureService.render(FeatureService.kt)
                at com.example.NativeBridge.invoke(Native Method)
                at com.example.EntryPoint.main(Unknown Source)
            """.trimIndent(),
        )

        assertEquals("kotlin.NullPointerException", parsed.exceptionType)
        assertEquals("name was null", parsed.exceptionMessage)
        assertEquals(4, parsed.frames.size)
        assertEquals("FeatureService.kt", parsed.frames[1].fileName)
        assertNull(parsed.frames[1].lineNumber)
        assertNull(parsed.frames[2].fileName)
        assertNull(parsed.frames[2].lineNumber)
        assertNull(parsed.frames[3].fileName)
        assertNull(parsed.frames[3].lineNumber)
    }

    @Test
    fun `parses caused by header when it is the first throwable line`() {
        val parsed = parser.parse(
            """
            Request failed while processing checkout
            Caused by: java.io.IOException: socket closed
                at com.example.net.HttpClient.execute(HttpClient.kt:88)
            """.trimIndent(),
        )

        assertEquals("java.io.IOException", parsed.exceptionType)
        assertEquals("socket closed", parsed.exceptionMessage)
        assertEquals(1, parsed.frames.size)
    }

    @Test
    fun `handles invalid input safely`() {
        val parsed = parser.parse(
            """
            This is not a stack trace.
            It should not crash the parser.
            """.trimIndent(),
        )

        assertNull(parsed.exceptionType)
        assertNull(parsed.exceptionMessage)
        assertTrue(parsed.frames.isEmpty())
    }
}
