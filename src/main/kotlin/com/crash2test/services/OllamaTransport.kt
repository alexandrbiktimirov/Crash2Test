package com.crash2test.services

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface OllamaTransport {
    fun postJson(uri: URI, body: String, timeout: Duration): OllamaHttpResponse

    fun streamJson(uri: URI, body: String, timeout: Duration, onLine: (String) -> Unit): OllamaHttpResponse
}

data class OllamaHttpResponse(
    val statusCode: Int,
    val body: String,
)

class HttpOllamaTransport(
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
) : OllamaTransport {
    override fun postJson(uri: URI, body: String, timeout: Duration): OllamaHttpResponse {
        val request = buildRequest(uri, body, timeout)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return OllamaHttpResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    override fun streamJson(uri: URI, body: String, timeout: Duration, onLine: (String) -> Unit): OllamaHttpResponse {
        val request = buildRequest(uri, body, timeout)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val responseBody = StringBuilder()
        BufferedReader(InputStreamReader(response.body())).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                responseBody.appendLine(line)
                if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                    onLine(line)
                }
            }
        }

        return OllamaHttpResponse(
            statusCode = response.statusCode(),
            body = responseBody.toString(),
        )
    }

    private fun buildRequest(uri: URI, body: String, timeout: Duration): HttpRequest =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
}
