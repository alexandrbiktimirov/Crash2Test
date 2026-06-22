package com.crash2test.settings

import com.crash2test.services.OllamaClientConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class Crash2TestSettingsStateTest {
    @Test
    fun `uses Ollama defaults before settings are changed`() {
        val settings = Crash2TestSettingsState()

        val config = settings.toOllamaClientConfig()

        assertEquals(OllamaClientConfig.DEFAULT_BASE_URL, config.baseUrl)
        assertEquals(OllamaClientConfig.DEFAULT_MODEL, config.model)
    }

    @Test
    fun `updates config from trimmed persisted values`() {
        val settings = Crash2TestSettingsState()

        settings.update(
            ollamaBaseUrl = "  http://127.0.0.1:11435/  ",
            modelName = "  llama3.1  ",
        )

        val config = settings.toOllamaClientConfig()
        assertEquals("http://127.0.0.1:11435/", config.baseUrl)
        assertEquals("llama3.1", config.model)
    }

    @Test
    fun `falls back to defaults when persisted values are blank`() {
        val settings = Crash2TestSettingsState()

        settings.loadState(
            Crash2TestSettingsState.State(
                ollamaBaseUrl = "   ",
                modelName = "",
            ),
        )

        val state = settings.state
        assertEquals(OllamaClientConfig.DEFAULT_BASE_URL, state.ollamaBaseUrl)
        assertEquals(OllamaClientConfig.DEFAULT_MODEL, state.modelName)
    }
}
