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
                "chain" to "solana:devnet",
            ),
        )

        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["auth_token"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["private_key"])
        assertEquals(DiagnosticSanitizer.REDACTED, sanitized["seed"])
        assertEquals("solana:devnet", sanitized["chain"])
        assertFalse(sanitized.values.contains("raw-token"))
        assertFalse(sanitized.values.contains("raw-private-material"))
        assertFalse(sanitized.values.contains("raw-seed"))
    }

    @Test
    fun fingerprintIsDeterministicWithoutPersistingPayload() {
        val first = DiagnosticSanitizer.sha256("payload".encodeToByteArray())
        val second = DiagnosticSanitizer.sha256("payload".encodeToByteArray())

        assertEquals(first, second)
        assertEquals(64, first.length)
    }
}
