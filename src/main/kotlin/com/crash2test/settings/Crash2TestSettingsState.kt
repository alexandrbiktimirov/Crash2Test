package com.crash2test.settings

import com.crash2test.services.*
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "Crash2TestSettings", storages = [Storage("crash2test.xml")])
class Crash2TestSettingsState : PersistentStateComponent<Crash2TestSettingsState.State> {
    private var settings = State()
    override fun getState(): State = settings
    override fun loadState(state: State) { settings = state.normalized() }

    fun update(ollamaBaseUrl: String, modelName: String) {
        settings = settings.copy(ollamaBaseUrl = ollamaBaseUrl, modelName = modelName).normalized()
    }

    fun toOllamaClientConfig() = OllamaClientConfig(baseUrl = settings.ollamaBaseUrl, model = settings.modelName)

    fun createGenerator(keys: ApiKeyStore = PasswordSafeApiKeyStore()): CrashAnalysisGenerator {
        val state = settings.normalized()
        if (state.provider == AiProvider.OLLAMA) return OllamaClient(toOllamaClientConfig())
        return object : CrashAnalysisGenerator {
            override fun generateStreaming(prompt: String, onChunk: (String) -> Unit): OllamaResult {
                val key = try { keys.get(state.provider) } catch (_: Exception) {
                    return OllamaResult.Failure(OllamaErrorType.INVALID_REQUEST,
                        "Could not read the API key from the IDE password store. Check Crash2Test settings.")
                }
                return ApiClient(state.provider, state.urlFor(state.provider), state.modelFor(state.provider), key)
                    .generateStreaming(prompt, onChunk)
            }
        }
    }

    data class State(
        var ollamaBaseUrl: String = OllamaClientConfig.DEFAULT_BASE_URL,
        var modelName: String = OllamaClientConfig.DEFAULT_MODEL,
        var provider: AiProvider = AiProvider.OLLAMA,
        var apiBaseUrl: String = "",
        var apiModel: String = "",
        var providerUrls: MutableMap<String, String> = mutableMapOf(),
        var providerModels: MutableMap<String, String> = mutableMapOf(),
    ) {
        fun urlFor(value: AiProvider): String = if (value == AiProvider.OLLAMA) ollamaBaseUrl else
            providerUrls[value.name] ?: if (value == provider) apiBaseUrl.ifBlank { value.baseUrl } else value.baseUrl
        fun modelFor(value: AiProvider): String = if (value == AiProvider.OLLAMA) modelName else
            providerModels[value.name] ?: if (value == provider) apiModel.ifBlank { value.model } else value.model

        fun normalized() = copy(
            ollamaBaseUrl = ollamaBaseUrl.trim().ifEmpty { AiProvider.OLLAMA.baseUrl },
            modelName = modelName.trim().ifEmpty { AiProvider.OLLAMA.model },
            apiBaseUrl = apiBaseUrl.trim().ifEmpty { provider.baseUrl },
            apiModel = apiModel.trim().ifEmpty { provider.model },
            providerUrls = providerUrls.toMutableMap().apply {
                if (provider != AiProvider.OLLAMA) putIfAbsent(provider.name, apiBaseUrl.trim().ifEmpty { provider.baseUrl })
            },
            providerModels = providerModels.toMutableMap().apply {
                if (provider != AiProvider.OLLAMA) putIfAbsent(provider.name, apiModel.trim().ifEmpty { provider.model })
            },
        )
    }
    companion object { fun getInstance(): Crash2TestSettingsState = service() }
}
