package dev.mwalab.ui.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mwalab.R
import dev.mwalab.ui.components.DiagnosticValue
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.components.StateNotice
import dev.mwalab.ui.sessions.IdentityUiState

@Composable
fun LabIdentityScreen(identity: IdentityUiState, onCopy: (String) -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("identity-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Lab Identity", style = MaterialTheme.typography.headlineSmall) }
        item { LabSafetyBanner() }
        item {
            SectionCard("DEVNET TEST IDENTITY") {
                when (identity) {
                    IdentityUiState.Loading -> Text("Preparing protected Devnet test identity…")
                    IdentityUiState.Unavailable -> StateNotice("Identity unavailable",
                        stringResource(R.string.identity_unavailable), "Retry", onRetry)
                    is IdentityUiState.Ready -> {
                        DiagnosticValue("Public address", identity.publicAddress)
                        Button(onClick = { onCopy(identity.publicAddress) }) { Text(stringResource(R.string.copy_address)) }
                    }
                }
            }
        }
        item { SectionCard("Protected locally") {
            Text("The test identity is protected on this device. MWA Lab never displays a mnemonic, seed, or private key.")
        } }
        item { SectionCard("Devnet funding") {
            Text("Use Devnet SOL only. Fund this public address from a Devnet faucet when a test scenario requires it.")
        } }
    }
}
