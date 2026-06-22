package com.crash2test.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class Crash2TestSettingsConfigurable(
    private val settings: Crash2TestSettingsState = Crash2TestSettingsState.getInstance(),
) : Configurable {
    private var component: SettingsComponent? = null

    override fun getDisplayName(): String = "Crash2Test"

    override fun createComponent(): JComponent {
        val createdComponent = SettingsComponent()
        component = createdComponent
        reset()
        return createdComponent.panel
    }

    override fun isModified(): Boolean {
        val currentComponent = component ?: return false
        val state = settings.state.normalized()
        return currentComponent.ollamaBaseUrl != state.ollamaBaseUrl ||
            currentComponent.modelName != state.modelName
    }

    override fun apply() {
        val currentComponent = component ?: return
        settings.update(
            ollamaBaseUrl = currentComponent.ollamaBaseUrl,
            modelName = currentComponent.modelName,
        )
    }

    override fun reset() {
        val currentComponent = component ?: return
        val state = settings.state.normalized()
        currentComponent.ollamaBaseUrl = state.ollamaBaseUrl
        currentComponent.modelName = state.modelName
    }

    override fun disposeUIResources() {
        component = null
    }

    private class SettingsComponent {
        private val ollamaBaseUrlField = JBTextField()
        private val modelNameField = JBTextField()

        val panel: JPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Ollama URL", ollamaBaseUrlField, 1, false)
            .addLabeledComponent("Model name", modelNameField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        var ollamaBaseUrl: String
            get() = ollamaBaseUrlField.text.trim()
            set(value) {
                ollamaBaseUrlField.text = value
            }

        var modelName: String
            get() = modelNameField.text.trim()
            set(value) {
                modelNameField.text = value
            }
    }
}
