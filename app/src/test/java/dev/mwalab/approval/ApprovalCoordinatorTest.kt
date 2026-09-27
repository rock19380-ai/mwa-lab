package dev.mwalab.approval

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalCoordinatorTest {
    @Test
    fun approvalIsBoundToRequestIdAndSingleFlight() = runBlocking {
        val coordinator = ApprovalCoordinator(timeoutMillis = 5_000)
        val request = ApprovalRequest(
            sessionId = "s1",
            method = "sign_messages",
            dappIdentityName = "test",
            chain = "solana:devnet",
            payloadFingerprints = listOf("abc"),
            payloadLengths = listOf(3),
        )
        val result = async { coordinator.requestApproval(request) }
        yield()

        assertFalse(coordinator.approve("wrong"))
        assertTrue(coordinator.approve(request.requestId))
        assertEquals(ApprovalDecision.Approved, result.await())
    }
}
