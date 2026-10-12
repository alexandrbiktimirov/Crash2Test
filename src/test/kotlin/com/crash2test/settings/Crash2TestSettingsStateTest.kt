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
    @Test
    fun `persisted settings serialize provider profiles without credentials`() {
        val settings = Crash2TestSettingsState()
        settings.loadState(Crash2TestSettingsState.State(
            provider = com.crash2test.services.AiProvider.OPENAI,
            apiBaseUrl = "https://example.invalid/v1",
            apiModel = "account-model",
        ))
        val serialized = com.intellij.util.xmlb.XmlSerializer.serialize(settings.state)
        val xml = com.intellij.openapi.util.JDOMUtil.writeElement(serialized)
        kotlin.test.assertFalse(xml.contains("apiKey", ignoreCase = true))
        kotlin.test.assertFalse(xml.contains("password", ignoreCase = true))
        val restored = com.intellij.util.xmlb.XmlSerializer.deserialize(serialized, Crash2TestSettingsState.State::class.java)
        assertEquals("https://example.invalid/v1", restored.urlFor(com.crash2test.services.AiProvider.OPENAI))
        assertEquals("account-model", restored.modelFor(com.crash2test.services.AiProvider.OPENAI))
    }

    @Test
    fun `password store failures become sanitized analysis failures`() {
        val settings = Crash2TestSettingsState()
        val keys = object : ApiKeyStore {
            override fun get(provider: com.crash2test.services.AiProvider): String = error("private detail")
            override fun set(provider: com.crash2test.services.AiProvider, key: String) = error("unused")
        }
        kotlin.test.assertIs<com.crash2test.services.OllamaClient>(settings.createGenerator(keys))
        settings.loadState(Crash2TestSettingsState.State(provider = com.crash2test.services.AiProvider.OPENAI))
        val result = settings.createGenerator(keys).generateStreaming("prompt") {}
        kotlin.test.assertIs<com.crash2test.services.OllamaResult.Failure>(result)
        kotlin.test.assertFalse(result.message.contains("private detail"))
    }
}
