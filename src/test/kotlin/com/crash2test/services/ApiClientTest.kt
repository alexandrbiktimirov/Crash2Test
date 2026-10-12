package com.crash2test.services

import kotlin.test.*

class ApiClientTest {
    @Test fun `compatible provider sends bearer credentials and streams text`() {
        val chunks = mutableListOf<String>()
        val client = ApiClient(AiProvider.GROK, AiProvider.GROK.baseUrl, "test-model", "test-key",
            ApiTransport { uri, body, headers, line ->
                assertEquals("https://api.x.ai/v1/chat/completions", uri.toString())
                assertEquals("Bearer test-key", headers["Authorization"])
                assertTrue(body.contains("test-model"))
                assertFalse(body.contains("test-key"))
                line("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}")
                line("data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}")
                line("data: [DONE]")
                200
            })
        assertEquals(OllamaResult.Success("Hello world"), client.generateStreaming("analyze", chunks::add))
        assertEquals(listOf("Hello", " world"), chunks)
    }

    @Test fun `anthropic uses messages headers and text deltas`() {
        val client = ApiClient(AiProvider.ANTHROPIC, AiProvider.ANTHROPIC.baseUrl, "claude-test", "key",
            ApiTransport { uri, body, headers, line ->
                assertEquals("https://api.anthropic.com/v1/messages", uri.toString())
                assertEquals("key", headers["x-api-key"])
                assertEquals("2023-06-01", headers["anthropic-version"])
                assertTrue(body.contains("\"max_tokens\":8192"))
                line("event: content_block_delta")
                line("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Analysis\"}}")
                line("data: {\"type\":\"message_stop\"}")
                200
            })
        assertEquals(OllamaResult.Success("Analysis"), client.generateStreaming("analyze") {})
    }

    @Test fun `missing keys and invalid URLs never send requests`() {
        val transport = ApiTransport { _, _, _, _ -> error("Must not send") }
        assertEquals(OllamaErrorType.INVALID_REQUEST,
            (ApiClient(AiProvider.OPENAI, "https://api.openai.com/v1", "model", "", transport)
                .generateStreaming("prompt") {} as OllamaResult.Failure).type)
        assertEquals(OllamaErrorType.INVALID_REQUEST,
            (ApiClient(AiProvider.OPENAI, "file:///tmp", "model", "key", transport)
                .generateStreaming("prompt") {} as OllamaResult.Failure).type)
    }

    @Test fun `HTTP and stream errors fail instead of returning partial success`() {
        for (status in listOf(401, 403, 429, 500)) {
            val client = ApiClient(AiProvider.OPENAI, AiProvider.OPENAI.baseUrl, "model", "key",
                ApiTransport { _, _, _, _ -> status })
            val result = client.generateStreaming("prompt") {} as OllamaResult.Failure
            assertEquals(OllamaErrorType.SERVER, result.type)
            assertTrue(result.message.contains(status.toString()))
        }
        val client = ApiClient(AiProvider.ANTHROPIC, AiProvider.ANTHROPIC.baseUrl, "model", "key",
            ApiTransport { _, _, _, line -> line("data: {\"type\":\"error\",\"error\":{\"message\":\"secret\"}}"); 200 })
        assertIs<OllamaResult.Failure>(client.generateStreaming("prompt") {})
    }

    @Test fun `custom endpoint can omit key and exceptions do not leak secrets`() {
        val client = ApiClient(AiProvider.CUSTOM, "http://localhost:1234/v1", "local", "",
            ApiTransport { _, _, headers, _ -> assertTrue(headers.isEmpty()); throw java.io.IOException("secret") })
        val result = client.generateStreaming("prompt") {} as OllamaResult.Failure
        assertEquals(OllamaErrorType.CONNECTION, result.type)
        assertFalse(result.message.contains("secret"))
    }
    @Test fun `incomplete and output-limited streams are failures`() {
        for (ending in listOf("", "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\ndata: [DONE]")) {
            val client = ApiClient(AiProvider.OPENAI, AiProvider.OPENAI.baseUrl, "model", "dummy",
                ApiTransport { _, _, _, line ->
                    line("data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}")
                    ending.lines().forEach(line)
                    200
                })
            assertIs<OllamaResult.Failure>(client.generateStreaming("prompt") {})
        }
        val anthropic = ApiClient(AiProvider.ANTHROPIC, AiProvider.ANTHROPIC.baseUrl, "model", "dummy",
            ApiTransport { _, _, _, line ->
                line("data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"Partial\"}}")
                line("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}")
                line("data: {\"type\":\"message_stop\"}")
                200
            })
        assertIs<OllamaResult.Failure>(anthropic.generateStreaming("prompt") {})
    }

    @Test fun `empty completed streams malformed responses and timeouts fail safely`() {
        val scenarios = listOf<Pair<ApiTransport, OllamaErrorType>>(
            ApiTransport { _, _, _, line -> line("data: [DONE]"); 200 } to OllamaErrorType.EMPTY_RESPONSE,
            ApiTransport { _, _, _, line -> line("data: invalid-json"); 200 } to OllamaErrorType.INVALID_REQUEST,
            ApiTransport { _, _, _, _ -> throw java.net.SocketTimeoutException("secret") } to OllamaErrorType.TIMEOUT,
        )
        for ((transport, expected) in scenarios) {
            val result = ApiClient(AiProvider.OPENAI, AiProvider.OPENAI.baseUrl, "model", "dummy", transport)
                .generateStreaming("prompt") {} as OllamaResult.Failure
            assertEquals(expected, result.type)
            assertFalse(result.message.contains("secret"))
        }
    }
}
