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
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["raw_message_payload"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["raw_transaction_payload"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["raw_signature_bytes"])
        assertEquals("solana:devnet", sanitized["chain"])
        assertFalse(sanitized.values.contains("raw-token"))
        assertFalse(sanitized.values.contains("raw-private-material"))
        assertFalse(sanitized.values.contains("raw-seed"))
        assertFalse(sanitized.values.contains("raw-authorization-token"))
        assertFalse(sanitized.values.contains("raw-association-token"))
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
