package dev.mwalab.security

import java.security.MessageDigest

object DiagnosticSanitizer {
    const val REDACTED = "<redacted>"

    private val secretKeyFragments = listOf(
        "private",
        "seed",
        "mnemonic",
        "auth_token",
        "authtoken",
        "authorization_token",
        "association_token",
        "association_secret",
        "raw_message_payload",
        "raw_transaction_payload",
        "raw_signature",
        "secret",
        "credential",
        "encryption",
        "ciphertext",
    )

    fun sanitizeFields(fields: Map<String, String>): Map<String, String> =
        fields.mapValues { (key, value) ->
            if (isSensitiveKey(key)) REDACTED else value.take(MAX_SAFE_VALUE_LENGTH)
        }

    fun sha256(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString(separator = "") { "%02x".format(it) }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace('-', '_')
        return secretKeyFragments.any(normalized::contains)
    }

    private const val MAX_SAFE_VALUE_LENGTH = 512
}
