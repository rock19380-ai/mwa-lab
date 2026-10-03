package dev.mwalab.mwa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.approval.WalletEndpointScreen
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.ui.theme.MWALabTheme

class MobileWalletAdapterActivity : ComponentActivity() {
    private val approvalCoordinator by lazy { MwaLabComposition.approvalCoordinator() }
    private val authorizationApprovalCoordinator by lazy { MwaLabComposition.authorizationApprovalCoordinator() }
    private val simulationCoordinator by lazy { MwaLabComposition.simulationCoordinator(applicationContext) }

    private val sessionHost by lazy {
        MwaSessionHost(
            context = applicationContext,
            approvalCoordinator = approvalCoordinator,
            authorizationApprovalCoordinator = authorizationApprovalCoordinator,
            onSessionFinished = {
                if (!isFinishing && !isDestroyed) finish()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MWALabTheme {
                val approvalState by approvalCoordinator.state.collectAsState()
                val authorizationState by authorizationApprovalCoordinator.state.collectAsState()
                val simulationStates by simulationCoordinator.state.collectAsState()
                WalletEndpointScreen(
                    authorizationState = authorizationState,
                    signingState = approvalState,
                    onAuthorizationApprove = { authorizationApprovalCoordinator.approve(it) },
                    onAuthorizationReject = { authorizationApprovalCoordinator.reject(it) },
                    onSigningApprove = { approvalCoordinator.approve(it) },
                    onSigningReject = { approvalCoordinator.reject(it) },
                    simulationStates = simulationStates,
                    onSimulate = { simulationCoordinator.simulate(it) },
                )
            }
        }

        processAssociationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processAssociationIntent(intent)
    }

    override fun onDestroy() {
        sessionHost.close()
        super.onDestroy()
    }

    private fun processAssociationIntent(intent: Intent?) {
        val result = sessionHost.openAssociation(intent?.data)
        if (result is AssociationOpenResult.Rejected) finish()
    }
}
