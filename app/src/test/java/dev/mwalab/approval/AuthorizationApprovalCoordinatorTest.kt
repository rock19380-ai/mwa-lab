package dev.mwalab.approval

import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationApprovalCoordinatorTest {
    @Test
    fun approvalIsSingleFlightAndBoundToRequestId() = runBlocking {
        val coordinator = AuthorizationApprovalCoordinator(timeoutMillis = 5_000)
        val first = request("first", generation = 1)
        val second = request("second", generation = 2)
        val firstResult = async { coordinator.requestApproval(first) }
        yield()

        assertEquals(AuthorizationDecision.Busy, coordinator.requestApproval(second))
        assertFalse(coordinator.approve(second.requestId))
        assertTrue(coordinator.approve(first.requestId))
        assertEquals(AuthorizationDecision.Approved, firstResult.await())
    }

    @Test
    fun staleCompletionCannotResolveReplacementRequest() = runBlocking {
        val coordinator = AuthorizationApprovalCoordinator(timeoutMillis = 5_000)
        val first = request("first", generation = 1)
        val firstResult = async { coordinator.requestApproval(first) }
        yield()
        coordinator.cancelSession(first.sessionId, first.generation)
        assertEquals(AuthorizationDecision.Cancelled, firstResult.await())

        val replacement = request("replacement", generation = 2)
        val replacementResult = async { coordinator.requestApproval(replacement) }
        yield()
        assertFalse(coordinator.approve(first.requestId))
        assertTrue(coordinator.reject(replacement.requestId))
        assertEquals(AuthorizationDecision.Rejected, replacementResult.await())
    }

    @Test
    fun timeoutClearsStateAndAllowsNextRequest() = runBlocking {
        val coordinator = AuthorizationApprovalCoordinator(timeoutMillis = 20)
        assertEquals(AuthorizationDecision.Expired, coordinator.requestApproval(request("expired", 1)))

        val next = request("next", generation = 2)
        val result = async { coordinator.requestApproval(next) }
        yield()
        assertTrue(coordinator.approve(next.requestId))
        assertEquals(AuthorizationDecision.Approved, result.await())
    }

    @Test
    fun requestRejectsUnsafePresentationText() {
        val result = runCatching {
            request("bad\nlabel", generation = 1)
        }
        assertTrue(result.isFailure)
    }

    private fun request(label: String, generation: Long) = AuthorizationApprovalRequest(
        requestId = "request-$generation-$label",
        sessionId = "session-$generation",
        generation = generation,
        associationMode = AssociationMode.LOCAL,
        dappDisplayName = "dapp-$label",
        claimedUriDisplay = "https://example.invalid/test",
        callerPackage = null,
        verificationState = DappVerificationState.UNVERIFIED,
        chain = "solana:devnet",
        requestedFeatures = emptyList(),
        requestedAddressCount = 0,
    )
}
