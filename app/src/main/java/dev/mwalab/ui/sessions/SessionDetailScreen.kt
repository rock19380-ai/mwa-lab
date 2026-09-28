package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.security.DiagnosticSanitizer

@Composable
fun SessionDetailScreen(state: SessionDetailUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("protocol-timeline"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to sessions") } }
        when (state) {
            SessionDetailUiState.Loading -> item { Text("Loading persisted timeline…") }
            SessionDetailUiState.Missing -> item { Text("Session not found") }
            SessionDetailUiState.Error -> item {
                Text("Protocol timeline unavailable. Try loading it again.")
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
            is SessionDetailUiState.Ready -> {
                val summary = state.summary
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(summary.session.dappIdentityName ?: "Unknown dApp", style = MaterialTheme.typography.headlineSmall)
                        Text("SESSION ${sessionStatusText(summary.status)}", style = MaterialTheme.typography.titleMedium)
                        Text("SOLANA DEVNET · ${summary.session.cluster}")
                        Text("Started ${timestampText(summary.session.startedAtEpochMillis)}")
                        Text(summary.session.completedAtEpochMillis?.let { "Completed ${timestampText(it)}" }
                            ?: "Recorded open. Connection liveness is unknown; process interruption may leave unfinished history.")
                        Text("${summary.eventCount} events · ${summary.durationMillis?.let { "$it ms" } ?: "No recorded end"}")
                        summary.session.closeReason?.let { Text("Close reason: ${it.name}") }
                    }
                }
                item {
                    Text("GET_CAPABILITIES: NOT OBSERVABLE THROUGH PINNED WALLETLIB", style = MaterialTheme.typography.labelLarge)
                    Text("Configured capability profile is context only. Requests handled internally by walletlib are absent from this timeline.")
                }
                if (summary.events.isEmpty()) item { Text("No observed protocol methods in this session.") }
                items(summary.events, key = { it.eventId }) { event -> ProtocolEventCard(event) }
            }
        }
    }
}

@Composable
private fun ProtocolEventCard(event: ProtocolEvent) {
    Card(Modifier.fillMaxWidth().testTag("event-${event.sequence}")) {
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("#${event.sequence} ${event.method.name}", style = MaterialTheme.typography.titleMedium)
                Text("${event.outcome.name} · ${event.durationMillis} ms", style = MaterialTheme.typography.labelLarge)
                Text("Started ${timestampText(event.startedAtEpochMillis)}")
                Text("Completed ${timestampText(event.completedAtEpochMillis)}")
                Text(protocolErrorText(event.protocolErrorCode))
                Text("Failure source: ${event.failureSource.name}")
                SafeSummary("Request summary", event.requestSummary)
                SafeSummary("Response summary", event.responseSummary)
                event.capabilityContext?.takeIf { it.isNotEmpty() }?.let {
                    SafeSummary("Recorded capability context", it)
                }
            }
        }
    }
}

@Composable
private fun SafeSummary(label: String, fields: Map<String, String>) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    if (fields.isEmpty()) Text("No summary recorded")
    else DiagnosticSanitizer.sanitizeFields(fields).toSortedMap().forEach { (key, value) ->
        Text("$key: $value", style = MaterialTheme.typography.bodySmall)
    }
}
