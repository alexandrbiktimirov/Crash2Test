package com.crash2test.settings

import com.crash2test.services.OllamaClientConfig
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
    name = "Crash2TestSettings",
    storages = [Storage("crash2test.xml")],
)
class Crash2TestSettingsState : PersistentStateComponent<Crash2TestSettingsState.State> {
    private var settings = State()

    override fun getState(): State = settings

    override fun loadState(state: State) {
        settings = state.normalized()
    }

    fun update(ollamaBaseUrl: String, modelName: String) {
        settings = State(
            ollamaBaseUrl = ollamaBaseUrl.normalizedOrDefault(OllamaClientConfig.DEFAULT_BASE_URL),
            modelName = modelName.normalizedOrDefault(OllamaClientConfig.DEFAULT_MODEL),
        )
    }

    fun toOllamaClientConfig(): OllamaClientConfig = OllamaClientConfig(
        baseUrl = settings.ollamaBaseUrl,
        model = settings.modelName,
    )

    data class State(
        var ollamaBaseUrl: String = OllamaClientConfig.DEFAULT_BASE_URL,
        var modelName: String = OllamaClientConfig.DEFAULT_MODEL,
    ) {
        fun normalized(): State = State(
            ollamaBaseUrl = ollamaBaseUrl.normalizedOrDefault(OllamaClientConfig.DEFAULT_BASE_URL),
            modelName = modelName.normalizedOrDefault(OllamaClientConfig.DEFAULT_MODEL),
        )
    }

    companion object {
        fun getInstance(): Crash2TestSettingsState = service()
    }
}

private fun String.normalizedOrDefault(defaultValue: String): String =
    trim().takeIf(String::isNotEmpty) ?: defaultValue
