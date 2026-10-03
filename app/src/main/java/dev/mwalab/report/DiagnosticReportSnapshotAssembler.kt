package dev.mwalab.report

import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import dev.mwalab.simulation.SimulationRepository
import dev.mwalab.transaction.TransactionDiagnosticRepository

/** Builds a report solely from persisted repository reads. No UI or protocol authority is used. */
class DiagnosticReportSnapshotAssembler(
    private val sessions: SessionRepository,
    private val capabilities: CapabilitySnapshotRepository,
    private val transactions: TransactionDiagnosticRepository,
    private val simulations: SimulationRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun build(sessionId: String): DiagnosticReport? {
        var latest = readOnce(sessionId) ?: return null
        if (latest.session.completeness == ReportCompleteness.PARTIAL) return finish(latest,
            ReportWarning("OPEN_SESSION", "Session may still receive additional diagnostic evidence."))

        repeat(MAX_SNAPSHOT_PAIRS) {
            val next = readOnce(sessionId) ?: return null
            if (latest == next) return finish(next)
            latest = next
            if (latest.session.completeness == ReportCompleteness.PARTIAL) return finish(latest,
                ReportWarning("OPEN_SESSION", "Session may still receive additional diagnostic evidence."))
        }
        return finish(latest.copy(session = latest.session.copy(completeness = ReportCompleteness.PARTIAL)),
            ReportWarning("CHANGING_SESSION", "Diagnostic evidence changed during bounded report reads."))
    }

    private fun finish(report: DiagnosticReport, warning: ReportWarning? = null): DiagnosticReport =
        report.copy(generatedAtEpochMillis = nowEpochMillis().coerceAtLeast(0),
            warnings = java.util.Collections.unmodifiableList(
                (report.warnings + listOfNotNull(warning)).distinct()
                    .sortedWith(compareBy({ it.code }, { it.message }))))

    private suspend fun readOnce(sessionId: String): DiagnosticReport? {
        val summary: SessionSummary = sessions.getSession(sessionId) ?: return null
        val policy = ReportSanitizationPolicy()
        val session = summary.session
        val capability = capabilities.getSnapshot(sessionId)?.also {
            require(it.sessionId == sessionId) { "Capability snapshot belongs to another session" }
        }
        val selectedEvents = summary.events.sortedBy { it.sequence }
        if (selectedEvents.size > ReportLimits.MAX_EVENTS) policy.omitted()
        var remainingTransactions = MAX_TRANSACTIONS_TOTAL
        var remainingSimulations = MAX_SIMULATIONS_TOTAL
        val reportEvents = selectedEvents.take(ReportLimits.MAX_EVENTS).map { event ->
            val isTransactionEvent = event.method == ProtocolMethod.SIGN_TRANSACTIONS ||
                event.method == ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS
            val tx = if (isTransactionEvent) transactions.getForEvent(sessionId, event.eventId) else emptyList()
            val sim = if (isTransactionEvent) simulations.getForEvent(sessionId, event.eventId) else emptyList()
            require(tx.all { it.sessionId == sessionId && it.eventId == event.eventId })
            require(sim.all { it.target.sessionId == sessionId && it.target.eventId == event.eventId })
            if (tx.size > remainingTransactions || sim.size > remainingSimulations) policy.omitted()
            val selectedTx = tx.sortedBy { it.payloadIndex }.take(remainingTransactions)
            val selectedSim = sim.sortedWith(compareBy({ it.target.payloadIndex }, { it.attemptNumber }))
                .take(remainingSimulations)
            remainingTransactions -= selectedTx.size
            remainingSimulations -= selectedSim.size
            policy.event(event, selectedTx, selectedSim)
        }
        return DiagnosticReport(0L, session = ReportSession(policy.identity(session.id),
            policy.label(session.dappIdentityName), session.associationMode,
            session.identityVerificationState, session.startedAtEpochMillis,
            session.completedAtEpochMillis, session.durationMillis, summary.status,
            session.closeReason?.name,
            if (session.completedAtEpochMillis == null) ReportCompleteness.PARTIAL else ReportCompleteness.COMPLETE),
            capabilities = policy.capability(capability),
            events = java.util.Collections.unmodifiableList(ArrayList(reportEvents)),
            warnings = java.util.Collections.unmodifiableList(ArrayList(policy.warnings())),
            truncated = policy.truncated)
    }

    companion object {
        private const val MAX_SNAPSHOT_PAIRS = 3
        private const val MAX_TRANSACTIONS_TOTAL = 40
        private const val MAX_SIMULATIONS_TOTAL = 80
    }
}
