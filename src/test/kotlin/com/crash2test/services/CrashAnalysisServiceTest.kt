package com.crash2test.services

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CrashAnalysisServiceTest {
    @Test
    fun `rejects blank input before resolving or generating`() {
        val frameResolver = RecordingFrameResolver(emptyList())
        val generator = RecordingGenerator(OllamaResult.Success("unused"))
        val service = service(frameResolver, generator)

        val result = service.analyze("   ")

        assertEquals(
            CrashAnalysisResult.Failure("Enter a stack trace or runtime error before running analysis."),
            result,
        )
        assertNull(frameResolver.recordedParsedStackTrace)
        assertNull(generator.recordedPrompt)
    }

    @Test
    fun `rejects unrecognized input before resolving or generating`() {
        val frameResolver = RecordingFrameResolver(emptyList())
        val generator = RecordingGenerator(OllamaResult.Success("unused"))
        val service = service(frameResolver, generator)

        val result = service.analyze("plain log line")

        assertEquals(
            CrashAnalysisResult.Failure("Could not recognize a Java or Kotlin stack trace."),
            result,
        )
        assertNull(frameResolver.recordedParsedStackTrace)
        assertNull(generator.recordedPrompt)
    }

    @Test
    fun `returns successful analysis with parsed frames resolved files and streamed chunks`() {
        val frame = StackFrameInfo(
            className = "com.example.OrderService",
            methodName = "create",
            fileName = "OrderService.java",
            lineNumber = 42,
        )
        val resolvedFrame = ResolvedFrame(
            frame = frame,
            resolvedPath = "src/main/java/com/example/OrderService.java",
            navigationPath = null,
            lineNumber = 42,
            status = ResolvedFrame.ResolutionStatus.RESOLVED,
        )
        val generator = RecordingGenerator(
            result = OllamaResult.Success("Summary\nAI result"),
            chunks = listOf("Summary", "\nAI result"),
        )
        val service = service(RecordingFrameResolver(listOf(resolvedFrame)), generator)
        val streamedChunks = mutableListOf<String>()

        val result = service.analyzeStreaming(
            rawInput = """
                java.lang.IllegalStateException: boom
                    at com.example.OrderService.create(OrderService.java:42)
            """.trimIndent(),
            onChunk = streamedChunks::add,
        )

        assertIs<CrashAnalysisResult.Success>(result)
        assertEquals("java.lang.IllegalStateException", result.parsedStackTrace.exceptionType)
        assertEquals(listOf(resolvedFrame), result.resolvedFrames)
        assertEquals("Java", result.regressionTestLanguage.displayName)
        assertEquals("Summary\nAI result", result.analysisText)
        assertEquals(listOf("Summary", "\nAI result"), streamedChunks)
        assertContains(generator.recordedPrompt ?: "", "OrderService.create(OrderService.java:42)")
    }

    @Test
    fun `returns failure details when generator fails`() {
        val frame = StackFrameInfo(
            className = "com.example.OrderService",
            methodName = "create",
            fileName = "OrderService.kt",
            lineNumber = 7,
        )
        val resolvedFrame = ResolvedFrame(
            frame = frame,
            resolvedPath = "src/main/kotlin/com/example/OrderService.kt",
            navigationPath = null,
            lineNumber = 7,
            status = ResolvedFrame.ResolutionStatus.RESOLVED,
        )
        val service = service(
            frameResolver = RecordingFrameResolver(listOf(resolvedFrame)),
            generator = RecordingGenerator(
                OllamaResult.Failure(
                    type = OllamaErrorType.CONNECTION,
                    message = "Could not connect to Ollama.",
                ),
            ),
        )

        val result = service.analyze(
            """
            java.lang.IllegalStateException: boom
                at com.example.OrderService.create(OrderService.kt:7)
            """.trimIndent(),
        )

        assertIs<CrashAnalysisResult.Failure>(result)
        assertEquals("Could not connect to Ollama.", result.message)
        assertEquals("java.lang.IllegalStateException", result.parsedStackTrace?.exceptionType)
        assertEquals(listOf(resolvedFrame), result.resolvedFrames)
        assertEquals("Kotlin", result.regressionTestLanguage.displayName)
    }

    private fun service(
        frameResolver: FrameResolver,
        generator: CrashAnalysisGenerator,
    ) = CrashAnalysisService(
        stackTraceParser = StackTraceParser(),
        frameResolver = frameResolver,
        analysisGenerator = generator,
        resolveInReadAction = { computation -> computation() },
    )

    private class RecordingFrameResolver(
        private val resolvedFrames: List<ResolvedFrame>,
    ) : FrameResolver {
        var recordedParsedStackTrace: ParsedStackTrace? = null

        override fun resolve(parsedStackTrace: ParsedStackTrace): List<ResolvedFrame> {
            recordedParsedStackTrace = parsedStackTrace
            return resolvedFrames
        }
    }

    private class RecordingGenerator(
        private val result: OllamaResult,
        private val chunks: List<String> = emptyList(),
    ) : CrashAnalysisGenerator {
        var recordedPrompt: String? = null

        override fun generateStreaming(prompt: String, onChunk: (String) -> Unit): OllamaResult {
            recordedPrompt = prompt
            chunks.forEach(onChunk)
            return result
        }
    }
}
