package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.capabilities.CapabilitySnapshotSource
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.ui.transaction.transactionInspectorContent
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.ui.simulation.SimulationResultContent
import dev.mwalab.report.ReportCompleteness
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.SessionStatus
import dev.mwalab.session.SessionSummary
import dev.mwalab.ui.components.DiagnosticValue
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.components.StateNotice
import dev.mwalab.ui.components.StatusBadge

@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryCapabilities: () -> Unit = onRetry,
    onRetryTransactions: (String) -> Unit = { onRetry() },
    onRetrySimulations: (String) -> Unit = { onRetry() },
    reportExportState: ReportExportUiState = ReportExportUiState.Idle,
    onShareMarkdown: () -> Unit = {},
    onShareJson: () -> Unit = {},
    onCopySummary: () -> Unit = {},
) {
    var expandedTransactions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LazyColumn(Modifier.fillMaxSize().testTag("protocol-timeline"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to sessions") } }
        when (state) {
            SessionDetailUiState.Loading -> item { StateNotice("Loading timeline", "Loading persisted protocol history…") }
            SessionDetailUiState.Missing -> item { StateNotice("Session not found", "This persisted session is unavailable.", "Retry", onRetry) }
            SessionDetailUiState.Error -> item {
                StateNotice("Protocol timeline unavailable", "Try loading this session again.", "Retry", onRetry)
            }
            is SessionDetailUiState.Ready -> {
                val summary = state.summary
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(summary.session.dappIdentityName ?: "Unknown dApp", style = MaterialTheme.typography.headlineSmall)
                        Text("SOLANA DEVNET · ${summary.session.cluster}")
                        Text("Connection: ${summary.session.associationMode.name}")
                        Text("Identity status: ${summary.session.identityVerificationState.name}")
                    }
                }
                item { SessionHero(summary) }
                item { Text("PROTOCOL TIMELINE", style = MaterialTheme.typography.titleLarge) }
                if (summary.events.isEmpty()) item { StateNotice("No observed methods",
                    "No protocol method was recorded for this session.") }
                summary.events.forEach { event ->
                    item(key = "event-${event.eventId}") { ProtocolEventCard(event) }
                }
                item { CapabilitySnapshotSection(state.capabilities, onRetryCapabilities) }
                val transactionEvents = summary.events.filter {
                    it.method == ProtocolMethod.SIGN_TRANSACTIONS ||
                        it.method == ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS
                }
                if (transactionEvents.isEmpty()) item {
                    Text("Transaction diagnostics were not recorded for this session.")
                }
                transactionEvents.forEach { event ->
                    transactionEventDiagnostics(event,
                        state.transactions[event.eventId] ?: SessionTransactionUiState.Missing,
                        expandedTransactions, { key ->
                            expandedTransactions = if (key in expandedTransactions) expandedTransactions - key
                                else expandedTransactions + key
                        }, { onRetryTransactions(event.eventId) })
                    simulationEventDiagnostics(event,
                        state.simulations[event.eventId] ?: SessionSimulationUiState.Missing,
                        { onRetrySimulations(event.eventId) })
                }
                item { SectionCard("Session metadata") {
                    Text("Started ${timestampText(summary.session.startedAtEpochMillis)}")
                    Text(summary.session.completedAtEpochMillis?.let { "Completed ${timestampText(it)}" }
                        ?: "Recorded open. Connection liveness is unknown; process interruption may leave unfinished history.")
                    summary.session.closeReason?.let { Text("Close reason: ${it.name}") }
                } }
                item { ReportExportSection(state, reportExportState, onShareMarkdown, onShareJson, onCopySummary) }
            }
        }
    }
}

@Composable
private fun SessionHero(summary: SessionSummary) {
    val hero = sessionHeroPresentation(summary)
    SectionCard(if (summary.status == SessionStatus.FAIL) "SESSION FAILED" else "SESSION ${hero.status}") {
        StatusBadge("SESSION ${hero.status}", alert = summary.status == SessionStatus.FAIL)
        if (summary.status == SessionStatus.FAIL) {
            DiagnosticValue("Failed at", hero.failedMethod ?: "No failed method recorded")
            DiagnosticValue("Protocol result", hero.protocolResult ?: "No protocol error recorded")
            DiagnosticValue("Failure source", hero.failureSource ?: "UNKNOWN")
            hero.injectedFault?.let {
                StatusBadge("INJECTED", alert = true)
                DiagnosticValue("Injected fault", it)
            }
        } else if (summary.status == SessionStatus.ACTIVE) {
            Text("Recorded open · connection liveness unknown. History may be incomplete.")
        }
        DiagnosticValue("Duration", hero.duration)
        Text("${hero.eventCount} protocol events", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReportExportSection(
    detail: SessionDetailUiState.Ready,
    export: ReportExportUiState,
    onShareMarkdown: () -> Unit,
    onShareJson: () -> Unit,
    onCopySummary: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isPartial = detail.summary.session.completedAtEpochMillis == null ||
        when (export) {
            is ReportExportUiState.Ready -> export.completeness == ReportCompleteness.PARTIAL
            is ReportExportUiState.Sharing -> export.completeness == ReportCompleteness.PARTIAL
            is ReportExportUiState.Copied -> export.completeness == ReportCompleteness.PARTIAL
            else -> false
        }
    Card(Modifier.fillMaxWidth().testTag("sanitized-report-export")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SANITIZED DIAGNOSTIC REPORT", style = MaterialTheme.typography.titleMedium)
            Text("NO PRIVATE KEYS OR AUTH TOKENS", style = MaterialTheme.typography.labelLarge)
            if (isPartial) {
                Text("PARTIAL REPORT", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall)
                Text("This session may still receive additional diagnostic evidence.")
            }
            val faultIds = detail.summary.events.mapNotNull { event ->
                event.injectedFaultId?.takeIf { it != "NORMAL" && FaultCatalog.find(it) != null }
            }.distinct().sorted()
            if (faultIds.isNotEmpty()) {
                Text("INTENTIONAL TEST CONDITION", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall)
                Text(faultIds.joinToString())
                val sources = detail.summary.events.filter { it.injectedFaultId != null }
                    .map { it.failureSource.name }.distinct().sorted()
                Text("Recorded failure source: ${sources.joinToString()}")
            }
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("export-report")) {
                Text("EXPORT REPORT")
            }
            if (expanded) {
                val busy = export == ReportExportUiState.Building || export is ReportExportUiState.Sharing
                OutlinedButton(onClick = onShareMarkdown, enabled = !busy,
                    modifier = Modifier.testTag("share-markdown")) { Text("Share Markdown") }
                OutlinedButton(onClick = onShareJson, enabled = !busy,
                    modifier = Modifier.testTag("share-json")) { Text("Share JSON") }
                OutlinedButton(onClick = onCopySummary, enabled = !busy,
                    modifier = Modifier.testTag("copy-summary")) { Text("Copy Summary") }
                when (export) {
                    ReportExportUiState.Building -> Text("Building report from persisted evidence…")
                    is ReportExportUiState.Sharing -> Text("Opening Android Share Sheet…")
                    is ReportExportUiState.Ready -> Text("Share Sheet opened for ${export.format.name}.")
                    is ReportExportUiState.Copied -> Text("Sanitized summary copied.")
                    ReportExportUiState.Error -> Text("Report action failed. Please retry.")
                    ReportExportUiState.Idle -> Unit
                }
                if (export is ReportExportUiState.Ready && export.truncated ||
                    export is ReportExportUiState.Copied && export.truncated) {
                    Text("Report is truncated; see warnings in the full report.")
                }
                Text("Simulation is diagnostic evidence only, not a guarantee of submission success.")
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
    var detailsExpanded by rememberSaveable(event.eventId) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("event-${event.sequence}")) {
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("#${event.sequence} ${event.method.name}", style = MaterialTheme.typography.titleMedium)
                StatusBadge(when (event.outcome) {
                    ProtocolOutcome.SUCCESS -> "PASS"
                    ProtocolOutcome.FAILURE -> "FAIL"
                    ProtocolOutcome.CANCELLED -> "CANCELLED"
                }, alert = event.outcome == ProtocolOutcome.FAILURE)
                Text("${event.outcome.name} · ${event.durationMillis} ms", style = MaterialTheme.typography.labelLarge)
                if (event.protocolErrorCode != null) Text(protocolErrorText(event.protocolErrorCode))
                Text("Failure source: ${event.failureSource.name}")
                injectedConditionText(event)?.let { condition ->
                    Text("INTENTIONAL TEST CONDITION", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall)
                    Text(condition)
                }
                TextButton(onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.testTag("event-details-${event.sequence}")) {
                    Text(if (detailsExpanded) "Hide event details" else "Show event details")
                }
                if (detailsExpanded) {
                    Text("Started ${timestampText(event.startedAtEpochMillis)}")
                    Text("Completed ${timestampText(event.completedAtEpochMillis)}")
                    SafeSummary("Request summary", event.requestSummary)
                    SafeSummary("Response summary", event.responseSummary)
                    event.capabilityContext?.takeIf { it.isNotEmpty() }?.let {
                        SafeSummary("Recorded capability context", it)
                    }
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

private fun LazyListScope.transactionEventDiagnostics(
    event: ProtocolEvent,
    state: SessionTransactionUiState,
    expanded: List<String>,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
) {
    item(key = "diagnostics-${event.eventId}") {
        Card(Modifier.fillMaxWidth().padding(start = 12.dp).testTag("diagnostics-${event.eventId}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TRANSACTION DIAGNOSTICS · event #${event.sequence}", style = MaterialTheme.typography.titleSmall)
                when (state) {
                    SessionTransactionUiState.Loading -> Text("Loading recorded transaction diagnostics…")
                    SessionTransactionUiState.Missing -> Text("Transaction diagnostics were not recorded for this event.")
                    SessionTransactionUiState.Unavailable -> {
                        Text("Recorded transaction diagnostics unavailable. The protocol timeline remains separate.")
                        OutlinedButton(onClick = onRetry) { Text("Retry transaction diagnostics") }
                    }
                    is SessionTransactionUiState.Recorded -> Text("${state.summaries.size} recorded payloads · read-only diagnostics")
                }
            }
        }
    }
    if (state is SessionTransactionUiState.Recorded) state.summaries.forEach { summary ->
        val key = "transaction-${event.eventId}-${summary.payloadIndex}"
        transactionInspectorContent(summary, key in expanded) { onToggle(key) }
    }
}

private fun LazyListScope.simulationEventDiagnostics(
    event: ProtocolEvent,
    state: SessionSimulationUiState,
    onRetry: () -> Unit,
) {
    item(key = "simulation-heading-${event.eventId}") {
        Card(Modifier.fillMaxWidth().padding(start = 24.dp)
            .testTag("simulation-diagnostics-${event.eventId}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SIMULATION DIAGNOSTICS · event #${event.sequence}", style = MaterialTheme.typography.titleSmall)
                Text("Child diagnostic evidence only; the protocol event above remains authoritative.")
                when (state) {
                    SessionSimulationUiState.Loading -> Text("Loading recorded simulation attempts…")
                    SessionSimulationUiState.Missing ->
                        Text("Simulation was not recorded for this event.")
                    SessionSimulationUiState.Unavailable -> {
                        Text("Recorded simulation attempts unavailable. The protocol timeline remains separate.")
                        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry-simulations-${event.eventId}")) {
                            Text("Retry simulation diagnostics")
                        }
                    }
                    is SessionSimulationUiState.Recorded ->
                        Text("${state.attempts.size} recorded simulation attempts")
                }
            }
        }
    }
    if (state is SessionSimulationUiState.Recorded) {
        val latest = state.attempts.groupBy { it.target.payloadIndex }.mapValues { (_, attempts) ->
            attempts.maxOf { it.attemptNumber }
        }
        state.attempts.forEach { result ->
            val index = result.target.payloadIndex
            val tag = "persisted-simulation-${event.eventId}-$index-${result.attemptNumber}"
            item(key = result.simulationId) {
                Card(Modifier.fillMaxWidth().padding(start = 32.dp).testTag(tag)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payload ${index + 1} · attempt ${result.attemptNumber}" +
                            if (result.attemptNumber == latest[index]) " · LATEST" else "",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Transaction fingerprint: ${result.target.transactionFingerprintSha256}")
                        SimulationResultContent(result, tag)
                    }
                }
            }
        }
    }
}
