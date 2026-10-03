package dev.mwalab.report

import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.simulation.SimulationLimits
import dev.mwalab.simulation.SimulationResult
import dev.mwalab.transaction.DecodedInstruction
import dev.mwalab.transaction.TransactionSummary
import dev.mwalab.transaction.TransactionVersion

/** Explicit export allowlist. Persisted diagnostic strings are not automatically export-safe. */
class ReportSanitizationPolicy {
    private val warnings = linkedSetOf<ReportWarning>()
    var truncated: Boolean = false
        private set

    fun warnings(): List<ReportWarning> = warnings.sortedWith(compareBy({ it.code }, { it.message }))

    fun note(code: String, message: String) { warnings += ReportWarning(code, message) }

    fun omitted() {
        truncated = true
        note("OMITTED_UNSAFE_OR_EXCESS_EVIDENCE", "Some diagnostic fields or children were omitted by export limits.")
    }

    fun identity(value: String): String = if (value.length <= 128 &&
        value.matches(Regex("[A-Za-z0-9:._-]+")) && !looksSensitive(value)) value
        else { omitted(); "<redacted-id>" }

    fun label(value: String?): String? {
        if (value == null) return null
        if (looksSensitive(value)) { omitted(); return "<redacted>" }
        val cleaned = value.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
        if (cleaned != value || cleaned.length > ReportLimits.MAX_TEXT_LENGTH) omitted()
        return cleaned.take(ReportLimits.MAX_TEXT_LENGTH).ifBlank { null }
    }

    /** Only producer-known keys with typed value shapes are copied. */
    fun summary(fields: Map<String, String>?): Map<String, String>? {
        if (fields == null) return null
        val output = java.util.TreeMap<String, String>()
        var accepted = 0
        // Keep only the lexical first N safe fields while visiting the map once.
        // A hostile stored map cannot force another unbounded list or sort.
        for ((key, value) in fields) {
            val safe = safeSummaryValue(key, value) ?: continue
            accepted++
            output[key] = safe
            if (output.size > ReportLimits.MAX_SUMMARY_FIELDS) output.pollLastEntry()
        }
        if (accepted != fields.size || accepted > ReportLimits.MAX_SUMMARY_FIELDS) omitted()
        return java.util.Collections.unmodifiableMap(LinkedHashMap(output))
    }

    private fun safeSummaryValue(key: String, value: String): String? {
        val payload = Regex("payload_([0-9]{1,2})_(sha256|length)").matchEntire(key)
        if (key !in SAFE_SUMMARY_KEYS && payload == null) return null
        if (value.length > ReportLimits.MAX_TEXT_LENGTH || looksSensitive(value)) return null
        val count = Regex("[0-9]{1,12}")
        val token = Regex("[a-z][a-z0-9_]{0,63}")
        val hash = Regex("[0-9a-f]{64}")
        if (payload != null) {
            if (payload.groupValues[1].toInt() >= ReportLimits.MAX_TRANSACTIONS_PER_EVENT) return null
            return value.takeIf { if (payload.groupValues[2] == "sha256") hash.matches(it) else count.matches(it) }
        }
        return when (key) {
            "method" -> value.takeIf { token.matches(it) }
            "chain" -> value.takeIf { it in setOf("devnet", "solana:devnet", "<missing>") }
            "public_account" -> value.takeIf(::publicKey)
            "payload_count", "address_count", "requested_feature_count", "requested_address_count",
            "signed_payload_count", "submitted_count", "not_submitted_count" -> value.takeIf(count::matches)
            "sign_in_requested", "skip_preflight", "wait_for_commitment", "max_retries_present",
            "min_context_slot_present", "commitment_verified" -> value.takeIf { it == "true" || it == "false" }
            "result", "authorization_reference", "authorization_state", "network_validation", "rpc_network",
            "transaction_version", "commitment" -> value.takeIf(token::matches)
            else -> null
        }
    }

    fun capability(snapshot: CapabilitySnapshot?): ReportCapabilitySnapshot? = snapshot?.let {
        val versions = it.supportedTransactionVersions.filter(::safeCapabilityValue)
        val features = it.optionalFeatures.filter(::safeCapabilityValue)
        if (versions.size != it.supportedTransactionVersions.size ||
            features.size != it.optionalFeatures.size) omitted()
        ReportCapabilitySnapshot(it.capturedAtEpochMillis, it.source.name,
            it.maxTransactionsPerSigningRequest, it.maxMessagesPerSigningRequest,
            freeze(versions.sorted()), freeze(features.sorted()))
    }

    fun safeCapabilityValue(value: String): Boolean =
        value.length <= ReportLimits.MAX_TEXT_LENGTH && !looksSensitive(value) &&
            (value.matches(Regex("[a-z][a-z0-9._-]{0,31}")) ||
                value.matches(Regex("[a-z][a-z0-9+.-]*:[A-Za-z0-9._/-]+")))

    fun event(event: ProtocolEvent, transactions: List<TransactionSummary>, simulations: List<SimulationResult>):
        ReportProtocolEvent {
        val selectedTransactions = transactions.sortedBy { it.payloadIndex }
        val selectedSimulations = simulations.sortedWith(compareBy({ it.target.payloadIndex }, { it.attemptNumber }))
        if (selectedTransactions.size > ReportLimits.MAX_TRANSACTIONS_PER_EVENT ||
            selectedSimulations.size > ReportLimits.MAX_SIMULATIONS_PER_EVENT) omitted()
        val faultId = event.injectedFaultId?.takeIf { FaultCatalog.find(it) != null && it != "NORMAL" }
        if (event.injectedFaultId != null && faultId == null) omitted()
        return ReportProtocolEvent(identity(event.eventId), event.sequence, event.method,
            event.startedAtEpochMillis, event.completedAtEpochMillis, event.durationMillis,
            event.outcome, event.protocolErrorCode, event.failureSource, faultId,
            summary(event.requestSummary) ?: emptyMap(), summary(event.responseSummary) ?: emptyMap(),
            summary(event.capabilityContext),
            freeze(selectedTransactions.take(ReportLimits.MAX_TRANSACTIONS_PER_EVENT).map(::transaction)),
            freeze(selectedSimulations.take(ReportLimits.MAX_SIMULATIONS_PER_EVENT).map(::simulation)))
    }

    private fun transaction(summary: TransactionSummary): ReportTransactionDiagnostic {
        val instructions = summary.instructions.orEmpty()
        if (instructions.size > ReportLimits.MAX_INSTRUCTIONS_PER_TRANSACTION) omitted()
        val version = when (val v = summary.transactionVersion) {
            TransactionVersion.LEGACY -> "LEGACY"
            TransactionVersion.V0 -> "V0"
            TransactionVersion.UNKNOWN -> "UNKNOWN"
            is TransactionVersion.VERSIONED_UNSUPPORTED -> "UNSUPPORTED_${v.number}"
        }
        return ReportTransactionDiagnostic(summary.payloadIndex, summary.fingerprintSha256,
            summary.wireLength, version, summary.inspectionStatus, summary.signatureCount,
            summary.feePayer?.takeIf(::publicKey), summary.instructionCount,
            freeze(instructions.take(ReportLimits.MAX_INSTRUCTIONS_PER_TRANSACTION).map { instruction ->
                val decoded = instruction.decodedInstruction
                val amount = when (decoded) {
                    is DecodedInstruction.SystemTransfer -> decoded.lamports.toString()
                    is DecodedInstruction.SplTokenTransfer -> decoded.rawAmount.toString()
                    is DecodedInstruction.SplTokenTransferChecked -> decoded.rawAmount.toString()
                    else -> null
                }
                val source = when (decoded) {
                    is DecodedInstruction.SystemTransfer -> decoded.from
                    is DecodedInstruction.SplTokenTransfer -> decoded.source
                    is DecodedInstruction.SplTokenTransferChecked -> decoded.source
                    else -> null
                }
                val destination = when (decoded) {
                    is DecodedInstruction.SystemTransfer -> decoded.to
                    is DecodedInstruction.SplTokenTransfer -> decoded.destination
                    is DecodedInstruction.SplTokenTransferChecked -> decoded.destination
                    else -> null
                }
                val kind = when (decoded) {
                    DecodedInstruction.Unknown -> "UNKNOWN"
                    is DecodedInstruction.Unsupported -> "UNSUPPORTED"
                    is DecodedInstruction.Malformed -> "MALFORMED"
                    is DecodedInstruction.Unavailable -> "UNAVAILABLE"
                    is DecodedInstruction.SystemTransfer -> "SYSTEM_TRANSFER"
                    is DecodedInstruction.Memo -> "MEMO"
                    is DecodedInstruction.SplTokenTransfer -> "SPL_TOKEN_TRANSFER"
                    is DecodedInstruction.SplTokenTransferChecked -> "SPL_TOKEN_TRANSFER_CHECKED"
                }
                ReportInstruction(instruction.index, instruction.programId,
                    instruction.programName, kind, amount,
                    source?.takeIf(::publicKey), destination?.takeIf(::publicKey))
            }), freeze(summary.limitations.map { it.name }.sorted()), summary.error?.reason?.name)
    }

    private fun simulation(result: SimulationResult): ReportSimulationAttempt {
        val lines = result.programLogs.map(SimulationLimits::publicLogLine)
        if (lines.size > ReportLimits.MAX_LOG_LINES || lines.any { it.length > ReportLimits.MAX_TEXT_LENGTH }) omitted()
        return ReportSimulationAttempt(result.target.payloadIndex, result.attemptNumber,
            result.target.transactionFingerprintSha256, result.startedAtEpochMillis,
            result.durationMillis, result.outcome, result.failureSource, result.commitment,
            result.contextSlot, result.error?.kind, result.error?.instructionIndex,
            result.error?.instructionErrorKind, result.error?.customProgramErrorCode,
            result.rpcErrorCode, result.availabilityReason, result.unitsConsumed,
            freeze(lines.take(ReportLimits.MAX_LOG_LINES).map { it.take(ReportLimits.MAX_TEXT_LENGTH) }),
            result.logsTruncated || lines.size > ReportLimits.MAX_LOG_LINES ||
                lines.any { it.length > ReportLimits.MAX_TEXT_LENGTH })
    }

    private fun <T> freeze(values: List<T>): List<T> =
        java.util.Collections.unmodifiableList(ArrayList(values))

    private fun publicKey(value: String): Boolean = value.length in 32..44 &&
        value.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))

    private fun looksSensitive(value: String): Boolean = listOf("secret", "private_key", "private-key",
        "seed", "mnemonic", "auth_token", "auth-token", "association_token", "association-token",
        "association_uri", "association-uri", "remote_uri", "remote-uri",
        "association_public_key", "association-public-key", "reflector_id", "reflector-id",
        "reflector_token", "reflector-token", "reflector_secret", "reflector-secret",
        "credential", "ciphertext", "raw-auth", "raw-message", "raw-transaction")
        .any { value.contains(it, ignoreCase = true) }

    companion object {
        private val SAFE_SUMMARY_KEYS = setOf("method", "chain", "public_account", "payload_count",
            "address_count", "requested_feature_count", "requested_address_count", "signed_payload_count",
            "submitted_count", "not_submitted_count", "sign_in_requested", "skip_preflight",
            "wait_for_commitment", "max_retries_present", "min_context_slot_present",
            "commitment_verified", "result", "authorization_reference", "authorization_state",
            "network_validation", "rpc_network", "transaction_version", "commitment")
    }
}
