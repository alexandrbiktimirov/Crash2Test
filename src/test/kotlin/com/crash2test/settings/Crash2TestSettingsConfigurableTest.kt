package com.crash2test.settings

import com.crash2test.services.OllamaClientConfig
import com.intellij.ui.components.JBTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.awt.Container

class Crash2TestSettingsConfigurableTest {
    @Test
    fun `apply persists values from the settings form`() {
        val settings = Crash2TestSettingsState()
        val configurable = Crash2TestSettingsConfigurable(settings)
        val component = configurable.createComponent()
        val fields = component.findTextFields()

        fields[0].text = "http://127.0.0.1:11435"
        fields[1].text = "llama3.1"

        assertTrue(configurable.isModified())
        configurable.apply()

        assertFalse(configurable.isModified())
        assertEquals("http://127.0.0.1:11435", settings.state.ollamaBaseUrl)
        assertEquals("llama3.1", settings.state.modelName)
    }

    @Test
    fun `reset restores persisted values to the settings form`() {
        val settings = Crash2TestSettingsState()
        settings.update(
            ollamaBaseUrl = "http://127.0.0.1:11435",
            modelName = "llama3.1",
        )
        val configurable = Crash2TestSettingsConfigurable(settings)
        val component = configurable.createComponent()
        val fields = component.findTextFields()

        fields[0].text = "http://example.invalid"
        fields[1].text = "unused"
        configurable.reset()

        assertEquals("http://127.0.0.1:11435", fields[0].text)
        assertEquals("llama3.1", fields[1].text)
        assertFalse(configurable.isModified())
    }

    @Test
    fun `blank form values are saved as defaults`() {
        val settings = Crash2TestSettingsState()
        settings.update(
            ollamaBaseUrl = "http://127.0.0.1:11435",
            modelName = "llama3.1",
        )
        val configurable = Crash2TestSettingsConfigurable(settings)
        val component = configurable.createComponent()
        val fields = component.findTextFields()

        fields[0].text = " "
        fields[1].text = ""
        configurable.apply()

        assertEquals(OllamaClientConfig.DEFAULT_BASE_URL, settings.state.ollamaBaseUrl)
        assertEquals(OllamaClientConfig.DEFAULT_MODEL, settings.state.modelName)
    }

    @Test
    fun `provider selection applies credentials separately and reset restores selection`() {
        val storedKeys = mutableMapOf<com.crash2test.services.AiProvider, String>()
        val keys = object : ApiKeyStore {
            override fun get(provider: com.crash2test.services.AiProvider) = storedKeys[provider].orEmpty()
            override fun set(provider: com.crash2test.services.AiProvider, key: String) { storedKeys[provider] = key }
        }
        val settings = Crash2TestSettingsState()
        val configurable = Crash2TestSettingsConfigurable(settings, keys)
        val panel = configurable.createComponent() as Container
        fun descendants(parent: Container): List<java.awt.Component> = parent.components.toList() +
            parent.components.filterIsInstance<Container>().flatMap { descendants(it) }
        val controls = descendants(panel)
        val selector = controls.filterIsInstance<javax.swing.JComboBox<*>>().single()
        selector.selectedItem = com.crash2test.services.AiProvider.ANTHROPIC
        val fields = panel.findTextFields()
        assertEquals("https://api.anthropic.com/v1", fields[0].text)
        controls.filterIsInstance<javax.swing.JPasswordField>().single().text = "test-secret"
        configurable.apply()
        assertEquals(com.crash2test.services.AiProvider.ANTHROPIC, settings.state.provider)
        assertEquals("test-secret", storedKeys[com.crash2test.services.AiProvider.ANTHROPIC])
        assertFalse(configurable.isModified())
        selector.selectedItem = com.crash2test.services.AiProvider.OLLAMA
        assertEquals("http://localhost:11434", fields[0].text)
        configurable.reset()
        assertEquals(com.crash2test.services.AiProvider.ANTHROPIC, selector.selectedItem)
        assertFalse(configurable.isModified())
    }

    @Test
    fun `apply saves edited profiles even after switching and reopening settings`() {
        val stored = mutableMapOf<com.crash2test.services.AiProvider, String>()
        val keys = object : ApiKeyStore {
            override fun get(provider: com.crash2test.services.AiProvider) = stored[provider].orEmpty()
            override fun set(provider: com.crash2test.services.AiProvider, key: String) { stored[provider] = key }
        }
        val settings = Crash2TestSettingsState()
        val configurable = Crash2TestSettingsConfigurable(settings, keys)
        val panel = configurable.createComponent() as Container
        val selector = panel.components.filterIsInstance<javax.swing.JComboBox<*>>().single()
        selector.selectedItem = com.crash2test.services.AiProvider.OPENAI
        val fields = panel.findTextFields()
        fields[0].text = "https://gateway.example.invalid/v1"
        fields[1].text = "account-model"
        panel.components.filterIsInstance<javax.swing.JPasswordField>().single().text = "dummy-only"
        selector.selectedItem = com.crash2test.services.AiProvider.OLLAMA
        assertTrue(configurable.isModified())
        configurable.apply()
        assertFalse(configurable.isModified())
        val xml = com.intellij.openapi.util.JDOMUtil.writeElement(com.intellij.util.xmlb.XmlSerializer.serialize(settings.state))
        assertFalse(xml.contains("dummy-only"))
        assertFalse(xml.contains("apiKey", ignoreCase = true))
        configurable.disposeUIResources()
        val reopened = configurable.createComponent() as Container
        reopened.components.filterIsInstance<javax.swing.JComboBox<*>>().single().selectedItem = com.crash2test.services.AiProvider.OPENAI
        assertEquals("https://gateway.example.invalid/v1", reopened.findTextFields()[0].text)
        assertEquals("account-model", reopened.findTextFields()[1].text)
        assertEquals("dummy-only", String(reopened.components.filterIsInstance<javax.swing.JPasswordField>().single().password))
        // Clearing a credential removes its value from the password store.
        reopened.components.filterIsInstance<javax.swing.JPasswordField>().single().text = ""
        configurable.apply()
        assertEquals("", stored[com.crash2test.services.AiProvider.OPENAI])
    }

    @Test
    fun `credentials in endpoint URL are rejected before saving`() {
        val settings = Crash2TestSettingsState()
        val configurable = Crash2TestSettingsConfigurable(settings)
        val panel = configurable.createComponent() as Container
        for (url in listOf("https://user:dummy@example.invalid/v1", "https://example.invalid/v1?api_key=dummy", "file:///tmp", "https://example.invalid/#dummy")) {
            panel.findTextFields()[0].text = url
            kotlin.test.assertFailsWith<com.intellij.openapi.options.ConfigurationException> { configurable.apply() }
            assertEquals("http://localhost:11434", settings.state.ollamaBaseUrl)
        }
    }
    private fun Container.findTextFields(): List<JBTextField> {
        val directTextFields = components.filterIsInstance<JBTextField>()
        return directTextFields + components
            .filterIsInstance<Container>()
            .flatMap { child -> child.findTextFields() }
    }
}
