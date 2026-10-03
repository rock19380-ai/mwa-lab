package dev.mwalab.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticSanitizerTest {
    @Test
    fun secretBearingFieldsAreRedacted() {
        val sanitized = DiagnosticSanitizer.sanitizeFields(
            mapOf(
                "auth_token" to "raw-token",
                "private_key" to "raw-private-material",
                "seed" to "raw-seed",
                "authorization_token" to "raw-authorization-token",
                "association_token" to "raw-association-token",
                "association_uri" to "solana-wallet:/v1/associate/remote?token=raw",
                "remote_uri" to "solana-wallet:/v1/associate/remote?token=raw-2",
                "association_public_key" to "raw-association-public-key",
                "reflector_id" to "raw-reflector-id",
                "reflector_token" to "raw-reflector-token",
                "reflector_secret" to "raw-reflector-secret",
                "raw_message_payload" to "raw-message-bytes",
                "raw_transaction_payload" to "raw-transaction-bytes",
                "raw_signature_bytes" to "raw-signature-bytes",
                "chain" to "solana:devnet",
            ),
        )

        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["auth_token"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["private_key"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["seed"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["authorization_token"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["association_token"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["association_uri"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["remote_uri"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["association_public_key"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["reflector_id"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["reflector_token"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["reflector_secret"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["raw_message_payload"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["raw_transaction_payload"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["raw_signature_bytes"])
        assertEquals("solana:devnet", sanitized["chain"])
        assertFalse(sanitized.values.contains("raw-token"))
        assertFalse(sanitized.values.contains("raw-private-material"))
        assertFalse(sanitized.values.contains("raw-seed"))
        assertFalse(sanitized.values.contains("raw-authorization-token"))
        assertFalse(sanitized.values.contains("raw-association-token"))
        assertFalse(sanitized.values.any { it.contains("solana-wallet:/v1/associate/remote") })
        assertFalse(sanitized.values.contains("raw-association-public-key"))
        assertFalse(sanitized.values.contains("raw-reflector-id"))
        assertFalse(sanitized.values.contains("raw-reflector-token"))
        assertFalse(sanitized.values.contains("raw-reflector-secret"))
        assertFalse(sanitized.values.contains("raw-message-bytes"))
        assertFalse(sanitized.values.contains("raw-transaction-bytes"))
        assertFalse(sanitized.values.contains("raw-signature-bytes"))
    }

    @Test
    fun fingerprintIsDeterministicWithoutPersistingPayload() {
        val first = DiagnosticSanitizer.sha256("payload".encodeToByteArray())
        val second = DiagnosticSanitizer.sha256("payload".encodeToByteArray())

        assertEquals(first, second)
        assertEquals(64, first.length)
    }
}
