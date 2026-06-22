package com.crash2test.services

import java.net.ConnectException
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OllamaClientTest {
    @Test
    fun `uses mistral as the default model`() {
        val config = OllamaClientConfig()

        assertEquals("mistral", config.model)
        assertEquals(URI.create("http://localhost:11434/api/generate"), config.generateEndpoint)
    }

    @Test
    fun `sends non streaming generate request`() {
        val transport = RecordingTransport(
            response = OllamaHttpResponse(
                statusCode = 200,
                body = """{"response":"Summary output"}""",
            ),
        )
        val client = OllamaClient(transport = transport)

        val result = client.generate("Analyze this crash")

        assertIs<OllamaResult.Success>(result)
        assertEquals("Summary output", result.response)
        assertEquals(URI.create("http://localhost:11434/api/generate"), transport.recordedUri)
        assertEquals(Duration.ofSeconds(30), transport.recordedTimeout)
        assertTrue(transport.recordedBody!!.contains(""""model":"mistral""""))
        assertTrue(transport.recordedBody!!.contains(""""prompt":"Analyze this crash""""))
        assertTrue(transport.recordedBody!!.contains(""""stream":false"""))
    }

    @Test
    fun `returns model not found failure from Ollama error response`() {
        val client = OllamaClient(
            transport = RecordingTransport(
                response = OllamaHttpResponse(
                    statusCode = 404,
                    body = """{"error":"model 'mistral' not found"}""",
                ),
            ),
        )

        val result = client.generate("Analyze this crash")

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.MODEL_NOT_FOUND,
                message = "model 'mistral' not found",
            ),
            result,
        )
    }

    @Test
    fun `returns empty response failure when Ollama responds without content`() {
        val client = OllamaClient(
            transport = RecordingTransport(
                response = OllamaHttpResponse(
                    statusCode = 200,
                    body = """{"response":"   "}""",
                ),
            ),
        )

        val result = client.generate("Analyze this crash")

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.EMPTY_RESPONSE,
                message = "Ollama returned an empty response.",
            ),
            result,
        )
    }

    @Test
    fun `returns connection failure when Ollama is not running`() {
        val client = OllamaClient(
            transport = ThrowingTransport(ConnectException("Connection refused")),
        )

        val result = client.generate("Analyze this crash")

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.CONNECTION,
                message = "Could not connect to Ollama at http://localhost:11434. Make sure Ollama is running.",
            ),
            result,
        )
    }

    @Test
    fun `returns timeout failure when Ollama takes too long`() {
        val client = OllamaClient(
            transport = ThrowingTransport(TimeoutException("Timed out")),
        )

        val result = client.generate("Analyze this crash")

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.TIMEOUT,
                message = "Ollama did not respond within 30 seconds.",
            ),
            result,
        )
    }

    @Test
    fun `rejects blank prompts`() {
        val client = OllamaClient()

        val result = client.generate("   ")

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.INVALID_REQUEST,
                message = "Prompt must not be empty.",
            ),
            result,
        )
    }

    @Test
    fun `returns invalid request failure when configured URL is malformed`() {
        val client = OllamaClient(
            config = OllamaClientConfig(baseUrl = "not a valid url"),
            transport = RecordingTransport(
                response = OllamaHttpResponse(
                    statusCode = 200,
                    body = """{"response":"unused"}""",
                ),
            ),
        )

        val result = client.generate("Analyze this crash")

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.INVALID_REQUEST,
                message = "Ollama URL is invalid. Check Crash2Test settings.",
            ),
            result,
        )
    }

    @Test
    fun `streams partial responses and accumulates final text`() {
        val chunks = mutableListOf<String>()
        val transport = StreamingTransport(
            response = OllamaHttpResponse(
                statusCode = 200,
                body = """
                    {"response":"Sum","done":false}
                    {"response":"mary","done":false}
                    {"response":" output","done":true}
                """.trimIndent(),
            ),
        )
        val client = OllamaClient(transport = transport)

        val result = client.generateStreaming("Analyze this crash") { chunk ->
            chunks += chunk
        }

        assertIs<OllamaResult.Success>(result)
        assertEquals("Summary output", result.response)
        assertEquals(listOf("Sum", "mary", " output"), chunks)
        assertEquals(URI.create("http://localhost:11434/api/generate"), transport.recordedUri)
        assertEquals(Duration.ofSeconds(30), transport.recordedTimeout)
        assertTrue(transport.recordedBody!!.contains(""""stream":true"""))
    }

    @Test
    fun `returns server failure for streamed error payload`() {
        val client = OllamaClient(
            transport = StreamingTransport(
                response = OllamaHttpResponse(
                    statusCode = 200,
                    body = """{"error":"backend overloaded","done":true}""",
                ),
            ),
        )

        val result = client.generateStreaming("Analyze this crash") {}

        assertEquals(
            OllamaResult.Failure(
                type = OllamaErrorType.SERVER,
                message = "backend overloaded",
            ),
            result,
        )
    }

    private class RecordingTransport(
        private val response: OllamaHttpResponse,
    ) : OllamaTransport {
        var recordedUri: URI? = null
        var recordedBody: String? = null
        var recordedTimeout: Duration? = null

        override fun postJson(uri: URI, body: String, timeout: Duration): OllamaHttpResponse {
            recordedUri = uri
            recordedBody = body
            recordedTimeout = timeout
            return response
        }

        override fun streamJson(uri: URI, body: String, timeout: Duration, onLine: (String) -> Unit): OllamaHttpResponse {
            recordedUri = uri
            recordedBody = body
            recordedTimeout = timeout
            response.body.lineSequence().forEach(onLine)
            return response
        }
    }

    private class ThrowingTransport(
        private val exception: Exception,
    ) : OllamaTransport {
        override fun postJson(uri: URI, body: String, timeout: Duration): OllamaHttpResponse {
            throw exception
        }

        override fun streamJson(uri: URI, body: String, timeout: Duration, onLine: (String) -> Unit): OllamaHttpResponse {
            throw exception
        }
    }

    private class StreamingTransport(
        private val response: OllamaHttpResponse,
    ) : OllamaTransport {
        var recordedUri: URI? = null
        var recordedBody: String? = null
        var recordedTimeout: Duration? = null

        override fun postJson(uri: URI, body: String, timeout: Duration): OllamaHttpResponse {
            recordedUri = uri
            recordedBody = body
            recordedTimeout = timeout
            return response
        }

        override fun streamJson(uri: URI, body: String, timeout: Duration, onLine: (String) -> Unit): OllamaHttpResponse {
            recordedUri = uri
            recordedBody = body
            recordedTimeout = timeout
            response.body.lineSequence().forEach(onLine)
            return response
        }
    }
}
