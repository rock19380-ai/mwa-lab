package dev.mwalab.approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import dev.mwalab.ui.transaction.TransactionApprovalPresentation
import dev.mwalab.simulation.SimulationTargetRef
import dev.mwalab.simulation.SimulationUiState
import dev.mwalab.ui.simulation.SimulationResultContent
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard

/** Renders metadata; decision callbacks receive the original request ID. */
@Composable
fun SigningApprovalScreen(
    state: ApprovalState,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    simulationStates: Map<SimulationTargetRef, SimulationUiState> = emptyMap(),
    onSimulate: (SimulationTargetRef) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MWA LAB TEST ENDPOINT", style = MaterialTheme.typography.headlineSmall)
        Text("SOLANA DEVNET", style = MaterialTheme.typography.labelLarge)
        LabSafetyBanner()
        when (state) {
            ApprovalState.Idle -> {
                Text("Local Mobile Wallet Adapter session host")
                Text("Waiting for an authorized signing request.")
            }
            is ApprovalState.Pending -> {
                val request = state.request
                request.faultSnapshotId?.let { faultId ->
                    val profile = FaultCatalog.get(faultId)
                    SectionCard("FAULT ACTIVE · INTENTIONAL TEST CONDITION",
                        Modifier.testTag("request-fault-banner")) {
                        Text("Request fault snapshot: ${profile.displayName} (${faultId.stableId})")
                    }
                }
                Column(Modifier.weight(1f).testTag("approval-content").verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SIGNING APPROVAL", style = MaterialTheme.typography.titleLarge)
                    SectionCard("Request") {
                        Text("dApp: ${request.dappIdentityName ?: "Unknown dApp"}")
                        Text("Method: ${request.method}")
                        Text("Chain: ${request.chain}")
                        Text("Payloads: ${request.payloadFingerprints.size}")
                    }
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
                                request.simulationTargets.getOrNull(index)?.let { target ->
                                    val simulation = simulationStates[target] ?: SimulationUiState.NotRun
                                    OutlinedButton(
                                        onClick = { onSimulate(target) },
                                        enabled = simulation !is SimulationUiState.Running,
                                        modifier = Modifier.testTag("simulate-button-$index"),
                                    ) { Text("SIMULATE") }
                                    when (simulation) {
                                        SimulationUiState.NotRun -> Text("Simulation NOT RUN")
                                        SimulationUiState.Running -> Text("Simulation RUNNING",
                                            Modifier.testTag("simulation-$index-running"))
                                        is SimulationUiState.Completed ->
                                            SimulationResultContent(simulation.result, "simulation-$index")
                                    }
                                }
                            }
                        }
                        if (request.simulationTargets.any { it != null }) {
                            Text("Simulation is diagnostic evidence only. APPROVE and REJECT remain independent.")
                            Text("A successful simulation does not guarantee later submission or confirmation.")
                        }
                        if (diagnostics.omittedPayloadCount > 0) Text("Diagnostic payloads omitted: ${diagnostics.omittedPayloadCount}")
                    }
                }
                // Warnings and decision buttons remain visible while scrolling the request.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onReject(request.requestId) }, modifier = Modifier.weight(1f)) {
                        Text("REJECT")
                    }
                    Button(onClick = { onApprove(request.requestId) }, modifier = Modifier.weight(1f)) {
                        Text("APPROVE")
                    }
                }
            }
        }
    }
}
