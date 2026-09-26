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

        setContent {
            MaterialTheme {
                var state by remember {
                    mutableStateOf<DemoUiState>(DemoUiState.Running)
                }

                LaunchedEffect(Unit) {
                    state = withContext(Dispatchers.IO) {
                        runCatching {
                            DemoClientRunner(applicationContext).runCanonical()
                        }.fold(
                            onSuccess = { DemoUiState.Complete(it) },
                            onFailure = { DemoUiState.Failed },
                        )
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
                    Text("Canonical Phase 1 sequence")

                    when (val current = state) {
                        DemoUiState.Running -> Text("Running…")
                        DemoUiState.Failed -> Text("Result: FAIL")
                        is DemoUiState.Complete -> {
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
                    }
                }
            }
        }
    }
}

private sealed interface DemoUiState {
    data object Running : DemoUiState
    data object Failed : DemoUiState
    data class Complete(val result: DemoRunResult) : DemoUiState
}
