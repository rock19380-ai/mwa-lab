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
    @Test
    fun concurrentApprovalIsBusyAndCannotStealDecision() = runBlocking {
        val coordinator = ApprovalCoordinator(timeoutMillis = 5_000)
        val first = request("first")
        val second = request("second")

        val firstResult = async { coordinator.requestApproval(first) }
        yield()

        assertEquals(ApprovalDecision.Busy, coordinator.requestApproval(second))
        assertFalse(coordinator.approve(second.requestId))
        assertTrue(coordinator.approve(first.requestId))
        assertEquals(ApprovalDecision.Approved, firstResult.await())
    }

    @Test
    fun cancellationClearsPendingAndNextRequestCanProceed() = runBlocking {
        val coordinator = ApprovalCoordinator(timeoutMillis = 5_000)
        val first = request("first")
        val firstResult = async { coordinator.requestApproval(first) }
        yield()

        coordinator.cancelPending()
        assertEquals(ApprovalDecision.Cancelled, firstResult.await())

        val second = request("second")
        val secondResult = async { coordinator.requestApproval(second) }
        yield()
        assertTrue(coordinator.reject(second.requestId))
        assertEquals(ApprovalDecision.Rejected, secondResult.await())
    }

    @Test
    fun expiredApprovalClearsPendingState() = runBlocking {
        val coordinator = ApprovalCoordinator(timeoutMillis = 20)
        assertEquals(ApprovalDecision.Expired, coordinator.requestApproval(request("expired")))

        val next = request("next")
        val nextResult = async { coordinator.requestApproval(next) }
        yield()
        assertTrue(coordinator.approve(next.requestId))
        assertEquals(ApprovalDecision.Approved, nextResult.await())
    }

    @Test
    fun duplicateAndStaleCompletionAttemptsFailClosed() = runBlocking {
        val coordinator = ApprovalCoordinator(timeoutMillis = 5_000)
        val first = request("first")
        val firstResult = async { coordinator.requestApproval(first) }
        yield()

        assertTrue(coordinator.approve(first.requestId))
        assertFalse(coordinator.approve(first.requestId))
        assertEquals(ApprovalDecision.Approved, firstResult.await())

        val second = request("second")
        val secondResult = async { coordinator.requestApproval(second) }
        yield()
        assertFalse(coordinator.reject(first.requestId))
        assertTrue(coordinator.reject(second.requestId))
        assertEquals(ApprovalDecision.Rejected, secondResult.await())
    }

    private fun request(label: String) = ApprovalRequest(
        requestId = "request-$label",
        sessionId = "session-$label",
        method = "sign_messages",
        dappIdentityName = "test-$label",
        chain = "solana:devnet",
        payloadFingerprints = listOf("fingerprint-$label"),
        payloadLengths = listOf(label.length),
    )

}
