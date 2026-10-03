package dev.mwalab.approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard

@Composable
fun AuthorizationApprovalScreen(
    request: AuthorizationApprovalRequest,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("MWA LAB TEST ENDPOINT", style = MaterialTheme.typography.headlineSmall)
        Text("SOLANA DEVNET", style = MaterialTheme.typography.labelLarge)
        LabSafetyBanner()
        Column(
            Modifier
                .weight(1f)
                .testTag("authorization-content")
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CONNECT DAPP", style = MaterialTheme.typography.titleLarge)
            SectionCard("Connection request") {
                Text("dApp: ${request.dappDisplayName ?: "Unknown dApp"}")
                request.claimedUriDisplay?.let { Text("Claimed URI: $it") }
                request.callerPackage?.let { Text("Caller package: $it") }
                Text(
                    "Connection: " + when (request.associationMode) {
                        AssociationMode.LOCAL -> "SAME-DEVICE MWA"
                        AssociationMode.REMOTE -> "REMOTE MWA"
                    },
                )
                Text("Identity: ${verificationLabel(request.verificationState)}")
                Text("Network: SOLANA DEVNET")
                Text("Requested features: ${request.requestedFeatures.size}")
                Text("Requested addresses: ${request.requestedAddressCount}")
            }
            if (request.associationMode == AssociationMode.REMOTE) {
                SectionCard("REMOTE MWA") {
                    Text("Remote dApp identity is unverified unless stronger evidence is explicitly available.")
                    Text("Only continue if you initiated this test.")
                }
            }
            SectionCard("Developer test wallet") {
                Text("This is a protocol-testing wallet endpoint, not a production custody wallet.")
                Text("Never use real funds.")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onReject(request.requestId) },
                modifier = Modifier.weight(1f).testTag("authorization-reject"),
            ) { Text("REJECT") }
            Button(
                onClick = { onApprove(request.requestId) },
                modifier = Modifier.weight(1f).testTag("authorization-approve"),
            ) { Text("APPROVE") }
        }
    }
}

private fun verificationLabel(state: DappVerificationState): String = when (state) {
    DappVerificationState.VERIFIED -> "VERIFIED"
    DappVerificationState.UNVERIFIED -> "UNVERIFIED"
    DappVerificationState.NOT_AVAILABLE -> "NOT AVAILABLE"
    DappVerificationState.REMOTE_UNVERIFIED -> "UNVERIFIED REMOTE DAPP"
}
