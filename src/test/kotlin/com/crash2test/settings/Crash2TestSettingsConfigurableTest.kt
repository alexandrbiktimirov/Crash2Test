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

    private fun Container.findTextFields(): List<JBTextField> {
        val directTextFields = components.filterIsInstance<JBTextField>()
        return directTextFields + components
            .filterIsInstance<Container>()
            .flatMap { child -> child.findTextFields() }
    }
}
