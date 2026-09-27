package dev.mwalab.rpc

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DevnetSendOptionsTest {
    @Test
    fun `accepts pinned protocol commitment values`() {
        for (commitment in listOf(null, "processed", "confirmed", "finalized")) {
            assertNotNull(
                DevnetSendOptions(
                    minContextSlot = 0,
                    commitment = commitment,
                    skipPreflight = false,
                    maxRetries = 0,
                    waitForCommitmentToSendNextTransaction = true,
                ).validatedOrNull(),
            )
        }
    }

    @Test
    fun `rejects invalid numeric options and commitment`() {
        assertNull(
            DevnetSendOptions(-1, null, null, null, null).validatedOrNull(),
        )
        assertNull(
            DevnetSendOptions(null, null, null, -1, null).validatedOrNull(),
        )
        assertNull(
            DevnetSendOptions(null, "root", null, null, null).validatedOrNull(),
        )
    }
}
