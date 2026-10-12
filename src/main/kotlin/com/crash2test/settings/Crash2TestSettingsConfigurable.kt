package com.crash2test.settings

import com.crash2test.services.AiProvider
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class Crash2TestSettingsConfigurable(
    private val settings: Crash2TestSettingsState = Crash2TestSettingsState.getInstance(),
    private val keys: ApiKeyStore = PasswordSafeApiKeyStore(),
) : Configurable {
    private var component: SettingsComponent? = null
    private val drafts = mutableMapOf<AiProvider, Triple<String, String, String>>()
    private val loadedKeys = mutableMapOf<AiProvider, String>()
    private var selected = AiProvider.OLLAMA
    private var resetting = false
    override fun getDisplayName() = "Crash2Test"
    override fun createComponent(): JComponent {
        val form = SettingsComponent()
        component = form
        form.provider.addActionListener {
            if (!resetting) {
                drafts[selected] = Triple(form.url.text, form.model.text, String(form.key.password))
                selected = form.provider.selectedItem as AiProvider
                showProvider(form)
            }
        }
        reset()
        return form.panel
    }
    private fun saved(provider: AiProvider): Triple<String, String, String> {
        val state = settings.state.normalized()
        val url = state.urlFor(provider)
        val model = state.modelFor(provider)
        val key = if (provider == AiProvider.OLLAMA) "" else loadedKeys.getOrPut(provider) {
            try { keys.get(provider) } catch (_: Exception) {
                throw ConfigurationException("Could not read the API key from the IDE password store.")
            }
        }
        return Triple(url, model, key)
    }
    private fun showProvider(form: SettingsComponent) {
        val values = drafts[selected] ?: saved(selected)
        form.url.text = values.first
        form.model.text = values.second
        form.key.text = values.third
        form.key.isEnabled = selected != AiProvider.OLLAMA
        form.note.text = if (selected == AiProvider.OLLAMA) "Ollama runs locally. API key is not required." else
            "Analysis sends the stack trace and relevant source snippets to the configured API."
    }
    override fun isModified(): Boolean {
        val form = component ?: return false
        return selected != settings.state.provider || currentDrafts(form).any { (provider, draft) ->
            val saved = saved(provider)
            draft.first.trim() != saved.first || draft.second.trim() != saved.second || draft.third.trim() != saved.third
        }
    }
    private fun currentDrafts(form: SettingsComponent) = drafts +
        (selected to Triple(form.url.text, form.model.text, String(form.key.password)))

    override fun apply() {
        val form = component ?: return
        val pending = currentDrafts(form)
        // Validate every edited profile before writing any credentials or settings.
        pending.forEach { (provider, draft) ->
            val url = draft.first.trim().ifEmpty { provider.baseUrl }
            val uri = runCatching { java.net.URI.create(url) }.getOrNull()
            if (uri == null || uri.scheme !in listOf("http", "https") || uri.host == null ||
                uri.userInfo != null || uri.query != null || uri.fragment != null) {
                throw ConfigurationException("${provider.label}: use an HTTP(S) base URL without credentials, query parameters, or fragments. Enter the API key only in the API key field.")
            }
        }
        val state = settings.state.normalized().copy(provider = selected)
        pending.forEach { (provider, draft) ->
            if (provider == AiProvider.OLLAMA) {
                state.ollamaBaseUrl = draft.first
                state.modelName = draft.second
            } else {
                state.providerUrls[provider.name] = draft.first.trim().ifEmpty { provider.baseUrl }
                state.providerModels[provider.name] = draft.second.trim().ifEmpty { provider.model }
                val key = draft.third.trim()
                if (key != saved(provider).third) {
                    try { keys.set(provider, key) } catch (_: Exception) {
                        throw ConfigurationException("Could not save the ${provider.label} API key in the IDE password store. Settings were not applied.")
                    }
                    loadedKeys[provider] = key
                }
            }
        }
        if (selected != AiProvider.OLLAMA) {
            state.apiBaseUrl = state.providerUrls.getValue(selected.name)
            state.apiModel = state.providerModels.getValue(selected.name)
        }
        settings.loadState(state)
        drafts.clear()
        showProvider(form)
    }
    override fun reset() {
        val form = component ?: return
        resetting = true
        try {
            drafts.clear()
            loadedKeys.clear()
            selected = settings.state.provider
            form.provider.selectedItem = selected
            showProvider(form)
        } finally { resetting = false }
    }
    override fun disposeUIResources() { component?.key?.text = ""; component = null; drafts.clear(); loadedKeys.clear() }
    private class SettingsComponent {
        val provider = JComboBox(AiProvider.entries.toTypedArray())
        val url = JBTextField()
        val model = JBTextField()
        val key = JPasswordField()
        val note = JLabel()
        val panel: JPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("AI provider", provider, 1, false)
            .addLabeledComponent("Base URL (including API version)", url, 1, false)
            .addLabeledComponent("Model name", model, 1, false)
            .addLabeledComponent("API key", key, 1, false)
            .addComponent(note)
            .addComponentFillVertically(JPanel(), 0).panel
    }
}
