package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mwalab.faults.FaultProfile
import dev.mwalab.faults.FaultId

@Composable
fun HomeScreen(state: HomeUiState, onSessions: () -> Unit, onSession: (String) -> Unit,
    onRetry: () -> Unit, activeFault: FaultProfile? = null, onFaultLab: () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Persistent protocol debugger", style = MaterialTheme.typography.headlineSmall)
            Text("Inspect observed MWA requests, outcomes, and safe summaries after a session ends or the app restarts.")
        }
        item {
            Text("DEVNET TEST IDENTITY", style = MaterialTheme.typography.titleMedium)
            when (val identity = state.identity) {
                IdentityUiState.Loading -> Text("Preparing protected Devnet test identity…")
                IdentityUiState.Unavailable -> Text("Lab identity unavailable. Protocol requests must fail closed.")
                is IdentityUiState.Ready -> Text(identity.publicAddress)
            }
            Text("Identity secrets are protected and are never shown or exported.")
        }
        activeFault?.let { profile -> item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Active fault", style = MaterialTheme.typography.titleMedium)
                    Text(if (profile.id == FaultId.NORMAL) "Normal" else profile.displayName)
                    if (profile.id != FaultId.NORMAL) Text("FAULT ACTIVE · INTENTIONAL TEST CONDITION")
                    TextButton(onClick = onFaultLab) { Text("Open Fault Lab") }
                }
            }
        } }
        item { Button(onClick = onSessions) { Text("View sessions") } }
        state.lastSession?.let { summary ->
            item { Text("Last session", style = MaterialTheme.typography.titleMedium) }
            item { SessionCard(summary, onSession) }
            item { OutlinedButton(onClick = { onSession(summary.session.id) }) { Text("Open last session") } }
        }
        if (state.history == SessionsUiState.Empty) item { Text("No protocol sessions yet") }
        if (state.history == SessionsUiState.Error || state.identity == IdentityUiState.Unavailable) item {
            Text("Some persisted state is unavailable.")
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
