package dev.mwalab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.ui.theme.MWALabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val identityRepository = MwaLabComposition.identityRepository(applicationContext)

        setContent {
            MWALabTheme {
                MwaLabHome(identityRepository)
            }
        }
    }
}

private sealed interface IdentityUiState {
    data object Loading : IdentityUiState
    data class Ready(val identity: TestEndpointIdentity) : IdentityUiState
    data object Unavailable : IdentityUiState
}

@Composable
private fun MwaLabHome(identityRepository: IdentityRepository) {
    var identityState by remember { mutableStateOf<IdentityUiState>(IdentityUiState.Loading) }

    LaunchedEffect(identityRepository) {
        identityState = try {
            IdentityUiState.Ready(identityRepository.getOrCreate())
        } catch (_: Throwable) {
            // Deliberately do not surface exception details to the UI. Storage failures
            // remain fail-closed and must never become secret-bearing diagnostics.
            IdentityUiState.Unavailable
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "MWA LAB TEST ENDPOINT",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "SOLANA DEVNET",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "NO REAL FUNDS",
                style = MaterialTheme.typography.titleMedium,
            )

            when (val state = identityState) {
                IdentityUiState.Loading -> Text("Preparing protected Devnet test identity…")
                IdentityUiState.Unavailable -> Text(
                    "Lab identity unavailable. Protocol requests must fail closed.",
                )
                is IdentityUiState.Ready -> {
                    Text(
                        text = "DEVNET TEST IDENTITY",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.identity.displayAddress,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Identity secrets are protected and are never shown or exported.")
                }
            }
        }
    }
}
