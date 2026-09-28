package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(state: HomeUiState, onSessions: () -> Unit, onSession: (String) -> Unit, onRetry: () -> Unit) {
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
