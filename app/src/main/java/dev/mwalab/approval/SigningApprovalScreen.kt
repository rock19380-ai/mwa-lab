package dev.mwalab.approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.ui.transaction.TransactionApprovalPresentation

/** Renders metadata; decision callbacks receive the original request ID. */
@Composable
fun SigningApprovalScreen(state: ApprovalState, onApprove: (String) -> Unit, onReject: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MWA LAB TEST ENDPOINT", style = MaterialTheme.typography.headlineSmall)
        Text("SOLANA DEVNET")
        Text("NO REAL FUNDS")
        when (state) {
            ApprovalState.Idle -> {
                Text("Local Mobile Wallet Adapter session host")
                Text("Waiting for an authorized signing request.")
            }
            is ApprovalState.Pending -> {
                val request = state.request
                Column(Modifier.weight(1f).testTag("approval-content").verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SIGNING APPROVAL", style = MaterialTheme.typography.titleLarge)
                    Text("dApp: ${request.dappIdentityName ?: "Unknown dApp"}")
                    Text("Method: ${request.method}")
                    Text("Chain: ${request.chain}")
                    Text("Payloads: ${request.payloadFingerprints.size}")
                    val diagnostics = request.transactionSummaries
                    if (diagnostics == null) {
                        request.payloadFingerprints.forEachIndexed { index, fingerprint ->
                            Text("#${index + 1} ${fingerprint.take(12)}… (${request.payloadLengths[index]} bytes)")
                        }
                    } else {
                        Text("Transaction diagnostics are informational; approval remains your decision.")
                        request.payloadFingerprints.forEachIndexed { index, fingerprint ->
                            Column(Modifier.testTag("approval-transaction-$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("TRANSACTION ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                val summary = diagnostics.summaries.getOrNull(index)
                                if (summary == null) {
                                    Text("Transaction diagnostics unavailable.")
                                    Text("Wire length: ${request.payloadLengths[index]} bytes")
                                    Text("MWA Lab payload fingerprint: $fingerprint")
                                } else {
                                    TransactionApprovalPresentation.lines(summary).forEach { Text(it) }
                                }
                            }
                        }
                        if (diagnostics.omittedPayloadCount > 0) Text("Diagnostic payloads omitted: ${diagnostics.omittedPayloadCount}")
                    }
                }
                // Warnings and decision buttons remain visible while scrolling the request.
                Button(onClick = { onApprove(request.requestId) }) { Text("APPROVE") }
                OutlinedButton(onClick = { onReject(request.requestId) }) { Text("REJECT") }
            }
        }
    }
}
