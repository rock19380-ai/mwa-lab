package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.session.SessionSummary

@Composable
fun SessionsScreen(state: SessionsUiState, onSession: (String) -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("sessions-list"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Protocol sessions", style = MaterialTheme.typography.headlineSmall) }
        when (state) {
            SessionsUiState.Loading -> item { Text("Loading persisted sessions…") }
            SessionsUiState.Empty -> item {
                Text("No protocol sessions yet", style = MaterialTheme.typography.titleMedium)
                Text("Connect an MWA-compatible test dApp to begin recording a session.")
            }
            SessionsUiState.Error -> item {
                Text("Session history unavailable. Try loading it again.")
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
            is SessionsUiState.Ready -> items(state.sessions, key = { it.session.id }) { summary ->
                SessionCard(summary, onSession)
            }
        }
    }
}

@Composable
fun SessionCard(summary: SessionSummary, onSession: (String) -> Unit) {
    Card(onClick = { onSession(summary.session.id) }, modifier = Modifier.fillMaxWidth()
        .testTag("session-${summary.session.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(summary.session.dappIdentityName ?: "Unknown dApp", style = MaterialTheme.typography.titleMedium)
            Text(sessionStatusText(summary.status), style = MaterialTheme.typography.labelLarge)
            Text("Started ${timestampText(summary.session.startedAtEpochMillis)}")
            Text("${summary.eventCount} events · ${summary.durationMillis?.let { "$it ms" } ?: "No recorded end"}")
            Text("SOLANA DEVNET · ${summary.session.cluster}", style = MaterialTheme.typography.labelMedium)
            summary.session.closeReason?.let { Text("Close reason: ${it.name}") }
            failureText(summary)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
