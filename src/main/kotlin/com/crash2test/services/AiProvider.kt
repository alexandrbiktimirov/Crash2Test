package com.crash2test.services

/** Base URLs include the API version; model IDs remain editable for account availability. */
enum class AiProvider(val label: String, val baseUrl: String, val model: String) {
    OLLAMA("Ollama", "http://localhost:11434", "mistral"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4.1"),
    ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1", "claude-sonnet-4-5"),
    GROK("xAI (Grok)", "https://api.x.ai/v1", "grok-4"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4.1"),
    CUSTOM("Custom OpenAI-compatible", "http://localhost:1234/v1", "local-model");

    override fun toString(): String = label
}
