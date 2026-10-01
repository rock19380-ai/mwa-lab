package dev.mwalab.report

import dev.mwalab.session.SessionCloseReason
import dev.mwalab.simulation.SimulationLimits
import dev.mwalab.transaction.KnownProgram

/** Rejects noncanonical or oversized reports before either renderer allocates output. */
internal object ReportOutputBounds {
    private val hash = Regex("[0-9a-f]{64}")
    private val key = Regex("[1-9A-HJ-NP-Za-km-z]{32,44}")
    private val token = Regex("[A-Z0-9_]{1,64}")
    private val version = Regex("(LEGACY|V0|UNKNOWN|UNSUPPORTED_[0-9]{1,3})")
    private val kinds = setOf("UNKNOWN", "UNSUPPORTED", "MALFORMED", "UNAVAILABLE",
        "SYSTEM_TRANSFER", "MEMO", "SPL_TOKEN_TRANSFER", "SPL_TOKEN_TRANSFER_CHECKED")
    private val warningMessages = mapOf(
        "OPEN_SESSION" to "Session may still receive additional diagnostic evidence.",
        "CHANGING_SESSION" to "Diagnostic evidence changed during bounded report reads.",
        "OMITTED_UNSAFE_OR_EXCESS_EVIDENCE" to "Some diagnostic fields or children were omitted by export limits.",
    )

    fun validate(report: DiagnosticReport) {
        require(report.generatedAtEpochMillis >= 0)
        require(report.application == ReportApplication() && report.environment == ReportEnvironment())
        require(report.session.sessionId == "<redacted-id>" ||
            ReportSanitizationPolicy().identity(report.session.sessionId) == report.session.sessionId)
        require(ReportSanitizationPolicy().label(report.session.dappDisplayName) == report.session.dappDisplayName)
        require(report.session.closeReason == null || SessionCloseReason.entries.any { it.name == report.session.closeReason })
        require((report.session.completedAtEpochMillis == null) == (report.session.closeReason == null))
        require(report.session.completedAtEpochMillis != null ||
            report.session.completeness == ReportCompleteness.PARTIAL)
        require(report.events.size <= ReportLimits.MAX_EVENTS && report.warnings.size <= 3)
        require(report.events.map { it.sequence } == report.events.map { it.sequence }.distinct().sorted())
        require(report.warnings == report.warnings.sortedWith(compareBy({ it.code }, { it.message })))
        require(report.warnings.all { warningMessages[it.code] == it.message })
        require(!report.truncated || report.warnings.any { it.code == "OMITTED_UNSAFE_OR_EXCESS_EVIDENCE" })
        require(report.session.completeness != ReportCompleteness.PARTIAL ||
            report.warnings.any { it.code == "OPEN_SESSION" || it.code == "CHANGING_SESSION" })
        var txTotal = 0
        var simTotal = 0
        report.events.forEach { e ->
            require(e.eventId == "<redacted-id>" || ReportSanitizationPolicy().identity(e.eventId) == e.eventId)
            require(e.sequence > 0 && e.durationMillis >= 0 && e.startedAtEpochMillis >= 0)
            require(e.completedAtEpochMillis >= e.startedAtEpochMillis)
            require(e.injectedFaultId == null || (e.injectedFaultId != "NORMAL" &&
                dev.mwalab.faults.FaultCatalog.find(e.injectedFaultId) != null))
            require(e.requestSummary.size <= ReportLimits.MAX_SUMMARY_FIELDS &&
                ReportSanitizationPolicy().summary(e.requestSummary) == e.requestSummary)
            require(e.responseSummary.size <= ReportLimits.MAX_SUMMARY_FIELDS &&
                ReportSanitizationPolicy().summary(e.responseSummary) == e.responseSummary)
            require(e.capabilityContext == null ||
                (e.capabilityContext.size <= ReportLimits.MAX_SUMMARY_FIELDS &&
                    ReportSanitizationPolicy().summary(e.capabilityContext) == e.capabilityContext))
            require(e.transactions.size <= ReportLimits.MAX_TRANSACTIONS_PER_EVENT &&
                e.simulations.size <= ReportLimits.MAX_SIMULATIONS_PER_EVENT)
            require(e.transactions.map { it.payloadIndex } ==
                e.transactions.map { it.payloadIndex }.distinct().sorted())
            require(e.simulations.map { it.payloadIndex to it.attemptNumber } ==
                e.simulations.map { it.payloadIndex to it.attemptNumber }
                    .distinct().sortedWith(compareBy({ it.first }, { it.second })))
            txTotal += e.transactions.size
            simTotal += e.simulations.size
            e.transactions.forEach { t ->
                require(t.payloadIndex >= 0 && hash.matches(t.fingerprintSha256) && t.wireLength >= 0)
                require(version.matches(t.transactionVersion) && t.instructions.size <= ReportLimits.MAX_INSTRUCTIONS_PER_TRANSACTION)
                require(t.feePayer == null || key.matches(t.feePayer))
                require(t.limitations.all(token::matches) && (t.error == null || token.matches(t.error)))
                t.instructions.forEach { i ->
                    require(i.index >= 0 && key.matches(i.programId) && i.decodedKind in kinds)
                    require(i.programName == null || KnownProgram.entries.any { it.displayName == i.programName })
                    require(i.exactAmount == null || i.exactAmount.matches(Regex("[0-9]{1,20}")))
                    require(i.source == null || key.matches(i.source))
                    require(i.destination == null || key.matches(i.destination))
                }
            }
            e.simulations.forEach { s ->
                require(s.payloadIndex >= 0 && s.attemptNumber > 0 && hash.matches(s.transactionFingerprintSha256))
                require(s.startedAtEpochMillis >= 0 && s.durationMillis >= 0)
                require(s.commitment in setOf("processed", "confirmed", "finalized"))
                require(s.programLogs.size <= ReportLimits.MAX_LOG_LINES)
                require(s.programLogs.all { it.length <= ReportLimits.MAX_TEXT_LENGTH &&
                    SimulationLimits.publicLogLine(it) == it })
            }
        }
        require(txTotal <= 40 && simTotal <= 80)
        report.capabilities?.let { c ->
            require(c.source == "CONFIGURED_WALLETLIB_PROFILE")
            require(c.supportedTransactionVersions.size <= 64 && c.optionalFeatures.size <= 64)
            require(c.supportedTransactionVersions.all { it.matches(Regex("[a-z][a-z0-9._-]{0,31}")) &&
                ReportSanitizationPolicy().safeCapabilityValue(it) })
            require(c.optionalFeatures.all { it.matches(Regex("[a-z][a-z0-9+.-]*:[A-Za-z0-9._/-]+")) &&
                ReportSanitizationPolicy().safeCapabilityValue(it) })
        }
    }
}
