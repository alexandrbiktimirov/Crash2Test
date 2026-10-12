package com.crash2test.services

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.test.*

class HttpApiTransportTest {
    @Test fun `real HTTP transport sends JSON and credentials and decodes UTF8 SSE`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        var authorization: String? = null
        server.createContext("/v1/chat/completions") { exchange ->
            requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
            authorization = exchange.requestHeaders.getFirst("Authorization")
            val data = "data: {\"choices\":[{\"delta\":{\"content\":\"Zażółć\"}}]}\n\ndata: [DONE]\n\n".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
            exchange.close()
        }
        server.start()
        try {
            val result = ApiClient(AiProvider.CUSTOM, "http://127.0.0.1:${server.address.port}/v1", "local", "dummy-credential")
                .generateStreaming("trace") {}
            assertEquals(OllamaResult.Success("Zażółć"), result)
            assertEquals("Bearer dummy-credential", authorization)
            assertTrue(requestBody.contains("trace"))
            assertFalse(requestBody.contains("dummy-credential"))
        } finally { server.stop(0) }
    }

    @Test fun `HTTP transport does not forward credentials to redirect targets`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var redirected = false
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "/destination")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.createContext("/destination") { exchange -> redirected = true; exchange.sendResponseHeaders(200, -1); exchange.close() }
        server.start()
        try {
            assertEquals(307, HttpApiTransport().stream(URI("http://127.0.0.1:${server.address.port}/start"), "{}",
                mapOf("Authorization" to "Bearer dummy")) {})
            assertFalse(redirected)
        } finally { server.stop(0) }
    }

    @Test fun `stream read times out when server stalls after headers`() {
        val executor = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = java.util.concurrent.CountDownLatch(1)
        server.executor = executor
        server.createContext("/stall") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("data: ".toByteArray())
                exchange.responseBody.flush()
                release.await()
            } finally { exchange.close() }
        }
        server.start()
        try {
            assertFailsWith<java.net.SocketTimeoutException> {
                HttpApiTransport(Duration.ofMillis(500)).stream(URI("http://127.0.0.1:${server.address.port}/stall"), "{}", emptyMap()) {}
            }
        } finally { release.countDown(); server.stop(0); executor.shutdownNow() }
    }
}
