package com.crash2test.settings

import com.crash2test.services.AiProvider
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe

interface ApiKeyStore {
    fun get(provider: AiProvider): String
    fun set(provider: AiProvider, key: String)
}

class PasswordSafeApiKeyStore : ApiKeyStore {
    private fun attributes(provider: AiProvider) = CredentialAttributes("Crash2Test/${provider.name}")
    override fun get(provider: AiProvider): String = PasswordSafe.instance.getPassword(attributes(provider)).orEmpty()
    override fun set(provider: AiProvider, key: String) {
        PasswordSafe.instance.setPassword(attributes(provider), key.takeIf { it.isNotBlank() })
    }
}
