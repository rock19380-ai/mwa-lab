package dev.mwalab.ui.sessions

import dev.mwalab.faults.FaultCatalog
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.SessionSummary

/** Read-only projection. Stored failure source and injected fault stay independent. */
data class SessionHeroPresentation(
    val status: String,
    val failedMethod: String?,
    val protocolResult: String?,
    val failureSource: String?,
    val injectedFault: String?,
    val duration: String,
    val eventCount: Int,
)

fun sessionHeroPresentation(summary: SessionSummary): SessionHeroPresentation {
    val failed = summary.events.firstOrNull { it.outcome == ProtocolOutcome.FAILURE }
    val fault = summary.events.mapNotNull { it.injectedFaultId }.distinct().map { id ->
        FaultCatalog.find(id)?.displayName?.let { "$it ($id)" } ?: id
    }.takeIf { it.isNotEmpty() }?.joinToString()
    return SessionHeroPresentation(
        status = summary.status.name,
        failedMethod = failed?.method?.name,
        protocolResult = failed?.let { protocolErrorText(it.protocolErrorCode) },
        failureSource = failed?.failureSource?.name,
        injectedFault = fault,
        duration = summary.durationMillis?.let { "$it ms" } ?: "No recorded end",
        eventCount = summary.eventCount,
    )
}
