package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.session.SessionStatus
import dev.mwalab.session.SessionSummary
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.StateNotice
import dev.mwalab.ui.components.StatusBadge

@Composable
fun SessionsScreen(state: SessionsUiState, onSession: (String) -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("sessions-list"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Protocol sessions", style = MaterialTheme.typography.headlineSmall) }
        item { LabSafetyBanner() }
        when (state) {
            SessionsUiState.Loading -> item { StateNotice("Loading sessions", "Loading persisted protocol history…") }
            SessionsUiState.Empty -> item { StateNotice("No protocol sessions yet",
                "Connect an MWA-compatible test dApp, such as the MWA Lab Demo Client, to begin recording a session.") }
            SessionsUiState.Error -> item { StateNotice("Session history unavailable.",
                "Persisted history could not be loaded.", "Retry", onRetry) }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(summary.session.dappIdentityName ?: "Unknown dApp", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(summary.status.name, alert = summary.status == SessionStatus.FAIL)
                if (sessionHasInjectedCondition(summary)) StatusBadge("INJECTED", alert = true)
            }
            Text("Started ${timestampText(summary.session.startedAtEpochMillis)}",
                style = MaterialTheme.typography.bodySmall)
            Text("${summary.eventCount} events · ${summary.durationMillis?.let { "$it ms" } ?: "No recorded end"}")
            Text("SOLANA DEVNET · ${summary.session.cluster}", style = MaterialTheme.typography.labelMedium)
            if (summary.status == SessionStatus.ACTIVE) Text("Recorded open · connection liveness unknown")
            if (summary.status == SessionStatus.CANCELLED) Text("Session interrupted")
            summary.session.closeReason?.let { Text("Close reason: ${it.name}", style = MaterialTheme.typography.bodySmall) }
            failureText(summary)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
