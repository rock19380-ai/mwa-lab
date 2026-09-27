package dev.mwalab.mwa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.approval.ApprovalState
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.ui.theme.MWALabTheme

class MobileWalletAdapterActivity : ComponentActivity() {
    private val approvalCoordinator by lazy { MwaLabComposition.approvalCoordinator() }

    private val sessionHost by lazy {
        MwaSessionHost(
            context = applicationContext,
            approvalCoordinator = approvalCoordinator,
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "MWA LAB TEST ENDPOINT",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("SOLANA DEVNET")
                    Text("NO REAL FUNDS")

                    when (val state = approvalState) {
                        ApprovalState.Idle -> {
                            Text("Local Mobile Wallet Adapter session host")
                            Text("Waiting for an authorized signing request.")
                        }

                        is ApprovalState.Pending -> {
                            val request = state.request
                            Text(
                                "SIGNING APPROVAL",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text("dApp: ${request.dappIdentityName ?: "Unknown dApp"}")
                            Text("Method: ${request.method}")
                            Text("Chain: ${request.chain}")
                            Text("Payloads: ${request.payloadFingerprints.size}")
                            request.payloadFingerprints.forEachIndexed { index, fingerprint ->
                                Text(
                                    "#${index + 1} ${fingerprint.take(12)}… " +
                                        "(${request.payloadLengths[index]} bytes)",
                                )
                            }
                            Button(
                                onClick = { approvalCoordinator.approve(request.requestId) },
                            ) {
                                Text("APPROVE")
                            }
                            OutlinedButton(
                                onClick = { approvalCoordinator.reject(request.requestId) },
                            ) {
                                Text("REJECT")
                            }
                        }
                    }
                }
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
