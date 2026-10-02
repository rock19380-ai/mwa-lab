package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mwalab.R
import dev.mwalab.faults.FaultId
import dev.mwalab.faults.FaultProfile
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.components.StateNotice
import dev.mwalab.ui.components.StatusBadge

@Composable
fun HomeScreen(
    state: HomeUiState, onSessions: () -> Unit, onSession: (String) -> Unit,
    onRetry: () -> Unit, activeFault: FaultProfile? = null, onFaultLab: () -> Unit = {},
    onIdentity: () -> Unit = {}, onCopyAddress: (String) -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("MWA Lab", style = MaterialTheme.typography.displaySmall)
                Text("MWA Protocol Lab · Solana Devnet", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.product_subtitle), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { LabSafetyBanner() }
        item {
            SectionCard("Active fault") {
                val profile = activeFault
                if (profile == null || profile.id == FaultId.NORMAL) {
                    StatusBadge("NORMAL")
                    Text("No intentional fault selected.")
                } else {
                    StatusBadge(stringResource(R.string.fault_active), alert = true)
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("FAULT ACTIVE · INTENTIONAL TEST CONDITION")
                }
                OutlinedButton(onClick = onFaultLab) { Text("Open Fault Lab") }
            }
        }
        item {
            SectionCard("Lab identity") {
                when (val identity = state.identity) {
                    IdentityUiState.Loading -> Text("Preparing protected Devnet test identity…")
                    IdentityUiState.Unavailable -> {
                        Text(stringResource(R.string.identity_unavailable))
                        OutlinedButton(onClick = onRetry) { Text("Retry identity") }
                    }
                    is IdentityUiState.Ready -> {
                        val address = identity.publicAddress
                        Text(if (address.length > 14) "${address.take(7)}…${address.takeLast(7)}" else address,
                            style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCopyAddress(address) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.copy_address))
                            }
                            OutlinedButton(onClick = onIdentity, modifier = Modifier.weight(1f)) {
                                Text("View identity")
                            }
                        }
                    }
                }
            }
        }
        item {
            when (val history = state.history) {
                SessionsUiState.Loading -> StateNotice("Latest session", "Loading persisted history…")
                SessionsUiState.Empty -> StateNotice("No sessions yet",
                    "Connect the MWA Lab Demo Client or a compatible MWA dApp to record a protocol session.")
                SessionsUiState.Error -> StateNotice("Session history unavailable",
                    "Persisted history could not be loaded.", "Retry", onRetry)
                is SessionsUiState.Ready -> {
                    val summary = history.sessions.firstOrNull()
                    if (summary == null) StateNotice("No sessions yet", "Connect a compatible MWA dApp.")
                    else SectionCard("Latest session") {
                        Text(summary.session.dappIdentityName ?: "Unknown dApp", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(summary.status.name, alert = summary.status.name == "FAIL")
                        Text("${summary.eventCount} events · ${summary.durationMillis?.let { "$it ms" } ?: "Recorded open"}")
                        failureText(summary)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { onSession(summary.session.id) }) { Text("Open Last Session") }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onSessions, modifier = Modifier.fillMaxWidth()) { Text("View Sessions") } }
    }
}
