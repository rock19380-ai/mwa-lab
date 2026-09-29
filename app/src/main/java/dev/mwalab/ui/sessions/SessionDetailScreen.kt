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
import dev.mwalab.capabilities.CapabilitySnapshotSource
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.security.DiagnosticSanitizer

@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryCapabilities: () -> Unit = onRetry,
) {
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
                    CapabilitySnapshotSection(state.capabilities, onRetryCapabilities)
                }
                if (summary.events.isEmpty()) item { Text("No observed protocol methods in this session.") }
                items(summary.events, key = { it.eventId }) { event -> ProtocolEventCard(event) }
            }
        }
    }
}

@Composable
private fun CapabilitySnapshotSection(state: SessionCapabilityUiState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("capability-snapshot")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("CAPABILITY SNAPSHOT", style = MaterialTheme.typography.titleMedium)
            when (state) {
                SessionCapabilityUiState.Loading -> Text("Loading recorded capability snapshot…")
                SessionCapabilityUiState.Missing -> Text("Capability snapshot was not recorded for this session.")
                SessionCapabilityUiState.Unavailable -> {
                    Text("Recorded capability snapshot unavailable. The protocol timeline remains separate.")
                    OutlinedButton(onClick = onRetry) { Text("Retry capability snapshot") }
                }
                is SessionCapabilityUiState.Recorded -> {
                    val snapshot = state.snapshot
                    val source = when (snapshot.source) {
                        CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE -> "Configured MWA Lab walletlib profile"
                    }
                    Text("Source: $source")
                    Text("Captured ${timestampText(snapshot.capturedAtEpochMillis)}")
                    Text("Max transactions/request: ${snapshot.maxTransactionsPerSigningRequest}")
                    Text("Max messages/request: ${snapshot.maxMessagesPerSigningRequest}")
                    Text("Transaction versions: ${snapshot.supportedTransactionVersions.joinToString()}")
                    Text("Optional features: ${snapshot.optionalFeatures.joinToString().ifEmpty { "None configured" }}")
                    Text("These values are the configured capability profile for this session.")
                }
            }
            Text("Observation note", style = MaterialTheme.typography.titleSmall)
            Text("walletlib 2.0.7 handles get_capabilities internally. No synthetic GET_CAPABILITIES timeline event was created.")
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
