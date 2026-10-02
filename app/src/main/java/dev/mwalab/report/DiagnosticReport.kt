package dev.mwalab.report

import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.SessionStatus
import dev.mwalab.simulation.SimulationAvailabilityReason
import dev.mwalab.simulation.SimulationErrorKind
import dev.mwalab.simulation.SimulationOutcome
import dev.mwalab.transaction.TransactionInspectionStatus

/** Safe, typed export surface. No raw wallet, RPC, transaction, or exception object belongs here. */
data class DiagnosticReport(
    val generatedAtEpochMillis: Long,
    val application: ReportApplication = ReportApplication(),
    val environment: ReportEnvironment = ReportEnvironment(),
    val session: ReportSession,
    val capabilities: ReportCapabilitySnapshot?,
    val events: List<ReportProtocolEvent>,
    val warnings: List<ReportWarning>,
    val truncated: Boolean,
) {
    val format: String get() = FORMAT
    val version: Int get() = VERSION

    companion object {
        const val FORMAT = "mwa-lab-diagnostic-report"
        const val VERSION = 1
    }
}

data class ReportApplication(val name: String = "MWA Lab", val walletlibVersion: String = "2.0.7")
data class ReportEnvironment(val cluster: String = "solana:devnet")
enum class ReportCompleteness { COMPLETE, PARTIAL }
data class ReportSession(
    val sessionId: String,
    val dappDisplayName: String?,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val durationMillis: Long?,
    val status: SessionStatus,
    val closeReason: String?,
    val completeness: ReportCompleteness,
)
data class ReportCapabilitySnapshot(
    val capturedAtEpochMillis: Long,
    val source: String,
    val maxTransactionsPerSigningRequest: Int,
    val maxMessagesPerSigningRequest: Int,
    val supportedTransactionVersions: List<String>,
    val optionalFeatures: List<String>,
)
data class ReportProtocolEvent(
    val eventId: String,
    val sequence: Long,
    val method: ProtocolMethod,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationMillis: Long,
    val outcome: ProtocolOutcome,
    val protocolErrorCode: Int?,
    val failureSource: ProtocolFailureSource,
    val injectedFaultId: String?,
    val requestSummary: Map<String, String>,
    val responseSummary: Map<String, String>,
    val capabilityContext: Map<String, String>?,
    val transactions: List<ReportTransactionDiagnostic>,
    val simulations: List<ReportSimulationAttempt>,
) {
    val protocolErrorName: String? get() = ReportProtocolErrorName.forCode(protocolErrorCode)
}
data class ReportTransactionDiagnostic(
    val payloadIndex: Int,
    val fingerprintSha256: String,
    val wireLength: Int,
    val transactionVersion: String,
    val inspectionStatus: TransactionInspectionStatus,
    val signatureCount: Int?,
    val feePayer: String?,
    val instructionCount: Int?,
    val instructions: List<ReportInstruction>,
    val limitations: List<String>,
    val error: String?,
)
data class ReportInstruction(
    val index: Int,
    val programId: String,
    val programName: String?,
    val decodedKind: String,
    val exactAmount: String?,
    val source: String?,
    val destination: String?,
)
data class ReportSimulationAttempt(
    val payloadIndex: Int,
    val attemptNumber: Int,
    val transactionFingerprintSha256: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val outcome: SimulationOutcome,
    val failureSource: ProtocolFailureSource,
    val commitment: String,
    val contextSlot: Long?,
    val errorKind: SimulationErrorKind?,
    val instructionIndex: Int?,
    val instructionErrorKind: String?,
    val customProgramErrorCode: Long?,
    val rpcErrorCode: Int?,
    val availabilityReason: SimulationAvailabilityReason?,
    val unitsConsumed: Long?,
    val programLogs: List<String>,
    val logsTruncated: Boolean,
)
data class ReportWarning(val code: String, val message: String)

object ReportLimits {
    const val MAX_EVENTS = 64
    const val MAX_TRANSACTIONS_PER_EVENT = 10
    const val MAX_SIMULATIONS_PER_EVENT = 20
    const val MAX_INSTRUCTIONS_PER_TRANSACTION = 8
    const val MAX_SUMMARY_FIELDS = 24
    const val MAX_TEXT_LENGTH = 128
    const val MAX_LOG_LINES = 16
    const val MAX_RENDERED_BYTES = 1024 * 1024
}
