package dev.mwalab.mwa

import dev.mwalab.app.MwaLabComposition
import dev.mwalab.approval.AuthorizationApprovalCoordinator
import dev.mwalab.approval.AuthorizationApprovalState
import java.util.concurrent.TimeUnit

/** Instrumentation-only helper. Production code never auto-approves authorization. */
internal object AuthorizationApprovalTestDriver {
    fun awaitPending(
        coordinator: AuthorizationApprovalCoordinator = MwaLabComposition.authorizationApprovalCoordinator(),
        timeoutSeconds: Long = 10,
    ): AuthorizationApprovalState.Pending {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            val state = coordinator.state.value
            if (state is AuthorizationApprovalState.Pending) return state
            Thread.sleep(25)
        }
        throw AssertionError("Timed out waiting for explicit authorization approval")
    }

    fun approveNext(
        coordinator: AuthorizationApprovalCoordinator = MwaLabComposition.authorizationApprovalCoordinator(),
    ): AuthorizationApprovalState.Pending = awaitPending(coordinator).also {
        check(coordinator.approve(it.request.requestId)) { "Authorization approval became stale" }
    }

    fun rejectNext(
        coordinator: AuthorizationApprovalCoordinator = MwaLabComposition.authorizationApprovalCoordinator(),
    ): AuthorizationApprovalState.Pending = awaitPending(coordinator).also {
        check(coordinator.reject(it.request.requestId)) { "Authorization rejection became stale" }
    }
}
