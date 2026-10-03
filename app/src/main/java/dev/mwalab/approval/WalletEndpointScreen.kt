package dev.mwalab.approval

import androidx.compose.runtime.Composable
import dev.mwalab.simulation.SimulationTargetRef
import dev.mwalab.simulation.SimulationUiState

/**
 * One wallet-host surface for authorization consent and existing signing
 * consent. Authorization has priority because signing cannot be valid before
 * authorization completes.
 */
@Composable
fun WalletEndpointScreen(
    authorizationState: AuthorizationApprovalState,
    signingState: ApprovalState,
    onAuthorizationApprove: (String) -> Unit,
    onAuthorizationReject: (String) -> Unit,
    onSigningApprove: (String) -> Unit,
    onSigningReject: (String) -> Unit,
    simulationStates: Map<SimulationTargetRef, SimulationUiState> = emptyMap(),
    onSimulate: (SimulationTargetRef) -> Unit = {},
) {
    when (authorizationState) {
        AuthorizationApprovalState.Idle -> SigningApprovalScreen(
            state = signingState,
            onApprove = onSigningApprove,
            onReject = onSigningReject,
            simulationStates = simulationStates,
            onSimulate = onSimulate,
        )
        is AuthorizationApprovalState.Pending -> AuthorizationApprovalScreen(
            request = authorizationState.request,
            onApprove = onAuthorizationApprove,
            onReject = onAuthorizationReject,
        )
    }
}
