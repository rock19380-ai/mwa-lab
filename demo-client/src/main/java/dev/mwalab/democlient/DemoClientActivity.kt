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

class DemoClientActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phase2Scenario = Phase2AcceptanceScenario.fromWireName(
            intent?.getStringExtra(EXTRA_PHASE2_SCENARIO),
        )

        setContent {
            MaterialTheme {
                var state by remember(phase2Scenario) {
                    mutableStateOf<DemoUiState>(DemoUiState.Running)
                }

                LaunchedEffect(phase2Scenario) {
                    state = withContext(Dispatchers.IO) {
                        if (phase2Scenario == null) {
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
                                onFailure = {
                                    DemoUiState.Failed("PHASE2 ${phase2Scenario.wireName}")
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

                    if (phase2Scenario == null) {
                        Text("Canonical Phase 1 sequence")
                    } else {
                        Text("Phase 2 device acceptance: ${phase2Scenario.wireName}")
                    }

                    when (val current = state) {
                        DemoUiState.Running -> Text("Running…")
                        is DemoUiState.Failed -> {
                            Text("${current.label}: FAIL")
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
    }
}

private sealed interface DemoUiState {
    data object Running : DemoUiState
    data class Failed(val label: String) : DemoUiState
    data class CanonicalComplete(val result: DemoRunResult) : DemoUiState
    data class Phase2Complete(val result: Phase2AcceptanceResult) : DemoUiState
}
