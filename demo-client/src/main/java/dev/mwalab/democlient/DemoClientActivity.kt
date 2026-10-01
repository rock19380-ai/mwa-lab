package dev.mwalab.democlient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import java.util.concurrent.ExecutionException

class DemoClientActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phase2Scenario = Phase2AcceptanceScenario.fromWireName(
            intent?.getStringExtra(EXTRA_PHASE2_SCENARIO),
        )

        // Explicit Phase 2 selection keeps its existing path and precedence.
        val phase4Scenario = if (phase2Scenario == null) Phase4AcceptanceScenario.fromWireName(
            intent?.getStringExtra(EXTRA_PHASE4_SCENARIO),
        ) else null

        val phase5Scenario = if (phase2Scenario == null && phase4Scenario == null)
            Phase5AcceptanceScenario.fromWireName(intent?.getStringExtra(EXTRA_PHASE5_SCENARIO)) else null

        val phase6Scenario = if (phase2Scenario == null && phase4Scenario == null && phase5Scenario == null)
            Phase6AcceptanceScenario.fromWireName(intent?.getStringExtra(EXTRA_PHASE6_SCENARIO)) else null

        setContent {
            MaterialTheme {
                var state by remember(phase2Scenario, phase4Scenario, phase5Scenario, phase6Scenario) {
                    mutableStateOf<DemoUiState>(DemoUiState.Running)
                }

                LaunchedEffect(phase2Scenario, phase4Scenario, phase5Scenario, phase6Scenario) {
                    state = withContext(Dispatchers.IO) {
                        if (phase6Scenario != null) {
                            runCatching { Phase6AcceptanceRunner(applicationContext).run(phase6Scenario) }.fold(
                                onSuccess = { DemoUiState.Phase6Complete(it) },
                                onFailure = { DemoUiState.Failed("PHASE6 ${phase6Scenario.name}", phase2FailureDetail(it)) },
                            )
                        } else if (phase5Scenario != null) {
                            runCatching {
                                Phase5AcceptanceRunner(applicationContext).run(phase5Scenario)
                            }.fold(
                                onSuccess = { DemoUiState.Phase5Complete(it) },
                                onFailure = { DemoUiState.Failed("PHASE5 ${phase5Scenario.name}", phase2FailureDetail(it)) },
                            )
                        } else if (phase4Scenario != null) {
                            runCatching {
                                Phase4AcceptanceRunner(applicationContext).run(phase4Scenario)
                            }.fold(
                                onSuccess = { DemoUiState.Phase4Complete(it) },
                                onFailure = { DemoUiState.Failed("PHASE4 ${phase4Scenario.name}", phase2FailureDetail(it)) },
                            )
                        } else if (phase2Scenario == null) {
                            runCatching {
                                DemoClientRunner(applicationContext).runCanonical()
                            }.fold(
                                onSuccess = { DemoUiState.CanonicalComplete(it) },
                                onFailure = { DemoUiState.Failed("PHASE1_CANONICAL") },
                            )
                        } else {
                            runCatching {
                                Phase2AcceptanceRunner(applicationContext).run(phase2Scenario)
                            }.fold(
                                onSuccess = { DemoUiState.Phase2Complete(it) },
                                onFailure = { failure ->
                                    DemoUiState.Failed(
                                        label = "PHASE2 ${phase2Scenario.wireName}",
                                        detail = phase2FailureDetail(failure),
                                    )
                                },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "MWA Lab Demo Client",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "FOR TESTING ONLY",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    if (phase6Scenario != null) {
                        Text("Phase 6 fault acceptance: ${phase6Scenario.name}")
                        Text("SOLANA DEVNET · TESTING ONLY")
                        Text("Select the matching profile manually in MWA Lab Fault Lab before this request.")
                        Text("This client verifies only its request and returned MWA result. Inspect wallet Session Detail for fault evidence.")
                        if (phase6Scenario.operation == Phase6AcceptanceScenario.Operation.SIGN_AND_SEND)
                            Text("Use only the MWA Lab Devnet test identity. Without the selected fault, normal submission may occur.")
                    } else if (phase5Scenario != null) {
                        Text("Phase 5 simulation: ${phase5Scenario.name}")
                        Text("SOLANA DEVNET · SIGN ONLY · NO SUBMISSION")
                        Text("In MWA Lab, tap SIMULATE; inspect PASS or FAIL, then " +
                            if (phase5Scenario.reject) "REJECT." else "APPROVE.")
                        Text("Simulation is diagnostic only; a PASS does not guarantee submission or confirmation.")
                    } else if (phase4Scenario != null) {
                        Text("Phase 4 transaction diagnostics: ${phase4Scenario.name}")
                        Text("SOLANA DEVNET · SIGN ONLY · NO SUBMISSION")
                    } else if (phase2Scenario == null) {
                        Text("Canonical Phase 1 sequence")
                    } else {
                        Text("Phase 2 device acceptance: ${phase2Scenario.wireName}")
                    }

                    when (val current = state) {
                        DemoUiState.Running -> Text("Running…")
                        is DemoUiState.Failed -> {
                            Text("${current.label}: FAIL")
                            current.detail?.let { Text("Failure: $it") }
                        }
                        is DemoUiState.CanonicalComplete -> {
                            Text("Result: PASS")
                            Text("Account: ${current.result.accountBase58}")
                            current.result.steps.forEach { step ->
                                Text(
                                    "${step.step}: " +
                                        if (step.passed) "PASS" else "FAIL",
                                )
                            }
                            Text(
                                "Capabilities: txMax=" +
                                    current.result.maxTransactionsPerSigningRequest +
                                    ", msgMax=" +
                                    current.result.maxMessagesPerSigningRequest +
                                    ", versions=" +
                                    current.result.supportedTransactionVersions.joinToString() +
                                    ", optional=" +
                                    current.result.optionalFeatures.joinToString(),
                            )
                        }
                        is DemoUiState.Phase6Complete -> {
                            val result = current.result
                            Text("PHASE6 ${result.scenario.name}: CLIENT RESULT VERIFIED")
                            Text("Requested operation: ${result.requestedOperation.name}")
                            Text("Received protocol error: ${result.receivedProtocolCode?.toString() ?: "none"}")
                            Text("Elapsed: ${result.elapsedMillis} ms")
                            Text("Signature verified: ${result.signatureVerified}")
                            Text("Wallet-side failure source and injected fault ID are recorded only by MWA Lab.")
                        }
                        is DemoUiState.Phase5Complete -> {
                            val result = current.result
                            Text("PHASE5 ${result.scenario.name}: PARENT RESPONSE VERIFIED")
                            Text("MWA Lab payload fingerprint: ${result.fingerprintSha256}")
                            Text("Wire length: ${result.wireLength} bytes")
                            Text("Fee payer: ${result.feePayer}")
                            Text("Signature verified: ${result.signatureVerified}")
                            result.protocolErrorCode?.let { Text("Expected protocol error: $it") }
                            Text("Submitted transactions: ${result.submittedTransactions}")
                            Text("Check MWA Lab Session Detail for independent simulation evidence.")
                        }
                        is DemoUiState.Phase4Complete -> {
                            val result = current.result
                            Text("PHASE4 ${result.scenario.name}: PASS")
                            Text("MWA Lab payload fingerprint: ${result.fingerprintSha256}")
                            Text("Wire length: ${result.wireLength} bytes")
                            Text("Fee payer: ${result.feePayer}")
                            Text("Recent blockhash: ${result.recentBlockhash}")
                            Text("Signature verified: ${result.signatureVerified}")
                            result.protocolErrorCode?.let { Text("Expected protocol error: $it") }
                        }
                        is DemoUiState.Phase2Complete -> {
                            val result = current.result
                            Text("PHASE2 ${result.scenario.wireName}: PASS")
                            Text("Evidence: ${result.summary}")
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PHASE2_SCENARIO = "mwa_phase2_scenario"
        const val EXTRA_PHASE4_SCENARIO = "mwa_phase4_scenario"
        const val EXTRA_PHASE5_SCENARIO = "mwa_phase5_scenario"
        const val EXTRA_PHASE6_SCENARIO = "mwa_phase6_scenario"
    }
}

private fun phase2FailureDetail(failure: Throwable): String {
    if (failure is Phase2FundingRequiredException) return checkNotNull(failure.message)

    val cause = unwrapPhase2Failure(failure)
    return when (cause) {
        is MobileWalletAdapterClient.InvalidPayloadsException ->
            "InvalidPayloadsException valid=${cause.validPayloads.joinToString(prefix = "[", postfix = "]")}"
        is JsonRpc20Client.JsonRpc20RemoteException ->
            "JsonRpc20RemoteException code=${cause.code}"
        else -> cause::class.java.simpleName
    }
}

private fun unwrapPhase2Failure(failure: Throwable): Throwable {
    var current = failure
    while (current is ExecutionException && current.cause != null) {
        current = current.cause!!
    }
    return current
}

private sealed interface DemoUiState {
    data object Running : DemoUiState
    data class Failed(val label: String, val detail: String? = null) : DemoUiState
    data class CanonicalComplete(val result: DemoRunResult) : DemoUiState
    data class Phase4Complete(val result: Phase4AcceptanceResult) : DemoUiState
    data class Phase5Complete(val result: Phase5AcceptanceResult) : DemoUiState
    data class Phase6Complete(val result: Phase6AcceptanceResult) : DemoUiState
    data class Phase2Complete(val result: Phase2AcceptanceResult) : DemoUiState
}
