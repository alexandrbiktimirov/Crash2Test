package com.crash2test.services

import kotlinx.serialization.json.*
import java.net.URI
import java.time.Duration

fun interface ApiTransport {
    fun stream(uri: URI, body: String, headers: Map<String, String>, onLine: (String) -> Unit): Int
}

class HttpApiTransport(
    private val timeout: Duration = Duration.ofSeconds(120),
) : ApiTransport {
    override fun stream(uri: URI, body: String, headers: Map<String, String>, onLine: (String) -> Unit): Int {
        val connection = uri.toURL().openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = minOf(timeout.toMillis(), 30_000L).toInt()
            connection.readTimeout = timeout.toMillis().toInt()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val deadline = System.nanoTime() + timeout.toNanos()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val remaining = deadline - System.nanoTime()
                        if (remaining <= 0) throw java.net.SocketTimeoutException()
                        connection.readTimeout = maxOf(1L, remaining / 1_000_000).toInt()
                        val line = reader.readLine() ?: break
                        onLine(line)
                    }
                }
            }
            return status
        } finally {
            connection.disconnect()
        }
    }
}
class ApiClient(
    private val provider: AiProvider,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val transport: ApiTransport = HttpApiTransport(),
) : CrashAnalysisGenerator {
    override fun generateStreaming(prompt: String, onChunk: (String) -> Unit): OllamaResult {
        fun failure(type: OllamaErrorType, message: String) = OllamaResult.Failure(type, message)
        if (prompt.isBlank() || model.isBlank()) return failure(OllamaErrorType.INVALID_REQUEST, "Prompt and model must not be empty.")
        if (apiKey.isBlank() && provider != AiProvider.CUSTOM) {
            return failure(OllamaErrorType.INVALID_REQUEST, "Add your ${provider.label} API key in Crash2Test settings.")
        }
        return try {
            val uri = URI.create(baseUrl.trim().trimEnd('/') + if (provider == AiProvider.ANTHROPIC) "/messages" else "/chat/completions")
            require(uri.scheme in listOf("http", "https") && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
            val headers = if (provider == AiProvider.ANTHROPIC) {
                mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
            } else if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
            val body = buildJsonObject {
                put("model", model)
                put("stream", true)
                if (provider == AiProvider.ANTHROPIC) put("max_tokens", 8192)
                putJsonArray("messages") {
                    addJsonObject { put("role", "user"); put("content", prompt) }
                }
            }.toString()
            val text = StringBuilder()
            var streamError = false
            var completed = false
            var truncated = false
            val status = transport.stream(uri, body, headers) { line ->
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") completed = true
                    if (data != "[DONE]" && data.isNotEmpty()) {
                        val event = Json.parseToJsonElement(data).jsonObject
                        if (event["error"] != null || event["type"]?.jsonPrimitive?.content == "error") streamError = true
                        if (event["type"]?.jsonPrimitive?.content == "message_stop") completed = true
                        val finishReason = if (provider == AiProvider.ANTHROPIC)
                            event["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                        else event["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                        if (finishReason in listOf("length", "max_tokens", "content_filter", "refusal")) truncated = true
                        val chunk = if (provider == AiProvider.ANTHROPIC) {
                            event["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        } else {
                            event["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                        }
                        if (!streamError && !chunk.isNullOrEmpty()) { text.append(chunk); onChunk(chunk) }
                    }
                }
            }
            when {
                status !in 200..299 -> failure(OllamaErrorType.SERVER, when (status) {
                    401, 403 -> "${provider.label} rejected the API key or model access (HTTP $status). Check Crash2Test settings."
                    429 -> "${provider.label} rate limit or quota exceeded (HTTP 429). Retry later or check your account."
                    else -> "${provider.label} request failed (HTTP $status). Check the endpoint and model in Crash2Test settings."
                })
                streamError -> failure(OllamaErrorType.SERVER, "${provider.label} reported an error while streaming. Retry or check your account.")
                truncated -> failure(OllamaErrorType.SERVER, "${provider.label} stopped before completing the analysis (output limit or content restriction).")
                !completed -> failure(OllamaErrorType.SERVER, "${provider.label} stream ended before completion. Retry the analysis.")
                text.isBlank() -> failure(OllamaErrorType.EMPTY_RESPONSE, "${provider.label} returned an empty response.")
                else -> OllamaResult.Success(text.toString().trim())
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            failure(OllamaErrorType.UNKNOWN, "Analysis was interrupted.")
        } catch (_: java.net.SocketTimeoutException) {
            failure(OllamaErrorType.TIMEOUT, "${provider.label} request timed out. Retry later.")
        } catch (_: IllegalArgumentException) {
            failure(OllamaErrorType.INVALID_REQUEST, "Invalid API URL or response. Check Crash2Test settings.")
        } catch (_: Exception) {
            // Do not surface exception text, which can contain request credentials.
            failure(OllamaErrorType.CONNECTION, "Could not complete the ${provider.label} request. Check your connection and API URL.")
        }
    }
}
