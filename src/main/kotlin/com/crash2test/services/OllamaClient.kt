package com.crash2test.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeoutException

interface CrashAnalysisGenerator {
    fun generateStreaming(prompt: String, onChunk: (String) -> Unit): OllamaResult
}

class OllamaClient(
    private val config: OllamaClientConfig = OllamaClientConfig(),
    private val transport: OllamaTransport = HttpOllamaTransport(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CrashAnalysisGenerator {
    fun generate(prompt: String): OllamaResult {
        val normalizedPrompt = prompt.trim()
        validatePrompt(normalizedPrompt)?.let { return it }

        return runRequest {
            val response = transport.postJson(
                uri = config.generateEndpoint,
                body = buildGenerateRequest(normalizedPrompt),
                timeout = config.requestTimeout,
            )
            parseGenerateResponse(response)
        }
    }

    override fun generateStreaming(
        prompt: String,
        onChunk: (String) -> Unit,
    ): OllamaResult {
        val normalizedPrompt = prompt.trim()
        validatePrompt(normalizedPrompt)?.let { return it }

        return runRequest {
            val accumulatedResponse = StringBuilder()
            var streamedFailure: OllamaResult.Failure? = null
            val response = transport.streamJson(
                uri = config.generateEndpoint,
                body = buildGenerateRequest(
                    prompt = normalizedPrompt,
                    stream = true,
                ),
                timeout = config.requestTimeout,
                onLine = { line ->
                    if (streamedFailure == null) {
                        streamedFailure = parseStreamingLine(line, accumulatedResponse, onChunk)
                    }
                },
            )
            parseStreamingGenerateResponse(response, accumulatedResponse, streamedFailure)
        }
    }

    private fun buildGenerateRequest(prompt: String, stream: Boolean): String =
        buildJsonObject {
            put("model", config.model)
            put("prompt", prompt)
            put("stream", stream)
        }.toString()

    private fun buildGenerateRequest(prompt: String): String = buildGenerateRequest(
        prompt = prompt,
        stream = false,
    )

    private fun parseGenerateResponse(response: OllamaHttpResponse): OllamaResult {
        val payload = parseJson(response.body)

        if (response.statusCode !in 200..299) {
            val errorMessage = payload
                ?.get("error")
                ?.let(::jsonStringValue)
                ?.takeIf { it.isNotBlank() }
                ?: "Ollama request failed with HTTP ${response.statusCode}."

            return OllamaResult.Failure(
                type = classifyServerError(errorMessage),
                message = errorMessage,
            )
        }

        val generatedText = payload
            ?.get("response")
            ?.let(::jsonStringValue)
            ?.trim()

        if (generatedText.isNullOrEmpty()) {
            return OllamaResult.Failure(
                type = OllamaErrorType.EMPTY_RESPONSE,
                message = "Ollama returned an empty response.",
            )
        }

        return OllamaResult.Success(generatedText)
    }

    private fun parseStreamingGenerateResponse(
        response: OllamaHttpResponse,
        accumulatedResponse: StringBuilder,
        streamedFailure: OllamaResult.Failure?,
    ): OllamaResult {
        if (response.statusCode !in 200..299) {
            return parseGenerateResponse(response)
        }

        streamedFailure?.let { return it }

        val generatedText = accumulatedResponse.toString().trim()
        if (generatedText.isEmpty()) {
            return OllamaResult.Failure(
                type = OllamaErrorType.EMPTY_RESPONSE,
                message = "Ollama returned an empty response.",
            )
        }

        return OllamaResult.Success(generatedText)
    }

    private fun parseStreamingLine(
        line: String,
        accumulatedResponse: StringBuilder,
        onChunk: (String) -> Unit,
    ): OllamaResult.Failure? {
        val payload = parseJson(line.trim()) ?: return null
        val errorMessage = payload["error"]?.let(::jsonStringValue)
        if (!errorMessage.isNullOrBlank()) {
            return OllamaResult.Failure(
                type = classifyServerError(errorMessage),
                message = errorMessage,
            )
        }

        val chunk = payload["response"]
            ?.let(::jsonStringValue)
            .orEmpty()
        if (chunk.isNotEmpty()) {
            accumulatedResponse.append(chunk)
            onChunk(chunk)
        }

        return null
    }

    private fun parseJson(body: String) = runCatching {
        json.parseToJsonElement(body).jsonObject
    }.getOrNull()

    private fun validatePrompt(prompt: String): OllamaResult.Failure? =
        prompt.takeIf(String::isEmpty)?.let {
            OllamaResult.Failure(
                type = OllamaErrorType.INVALID_REQUEST,
                message = "Prompt must not be empty.",
            )
        }

    private fun runRequest(block: () -> OllamaResult): OllamaResult = try {
        block()
    } catch (_: ConnectException) {
        OllamaResult.Failure(
            type = OllamaErrorType.CONNECTION,
            message = "Could not connect to Ollama at ${config.baseUrl}. Make sure Ollama is running.",
        )
    } catch (_: java.net.http.HttpTimeoutException) {
        timeoutFailure()
    } catch (_: TimeoutException) {
        timeoutFailure()
    } catch (exception: Exception) {
        OllamaResult.Failure(
            type = OllamaErrorType.UNKNOWN,
            message = exception.message ?: "Unexpected Ollama error.",
        )
    }

    private fun timeoutFailure() = OllamaResult.Failure(
        type = OllamaErrorType.TIMEOUT,
        message = "Ollama did not respond within ${config.requestTimeout.seconds} seconds.",
    )

    private fun classifyServerError(errorMessage: String) =
        if (errorMessage.contains("model", ignoreCase = true) &&
            errorMessage.contains("not found", ignoreCase = true)
        ) {
            OllamaErrorType.MODEL_NOT_FOUND
        } else {
            OllamaErrorType.SERVER
        }

    private fun jsonStringValue(element: kotlinx.serialization.json.JsonElement): String? = runCatching {
        element.jsonPrimitive.content
    }.getOrNull()
}

data class OllamaClientConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val requestTimeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
) {
    val generateEndpoint: URI
        get() = URI.create(baseUrl.trimEnd('/') + GENERATE_PATH)

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_MODEL = "mistral"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val GENERATE_PATH = "/api/generate"
    }
}

sealed interface OllamaResult {
    data class Success(val response: String) : OllamaResult

    data class Failure(
        val type: OllamaErrorType,
        val message: String,
    ) : OllamaResult
}

enum class OllamaErrorType {
    CONNECTION,
    TIMEOUT,
    MODEL_NOT_FOUND,
    EMPTY_RESPONSE,
    INVALID_REQUEST,
    SERVER,
    UNKNOWN,
}

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
        val request = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return OllamaHttpResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    override fun streamJson(uri: URI, body: String, timeout: Duration, onLine: (String) -> Unit): OllamaHttpResponse {
        val request = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

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
}
