package dev.mwalab.storage

import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionSummary
import dev.mwalab.storage.protocol.ProtocolEventEntity
import dev.mwalab.storage.session.SessionEntity
import dev.mwalab.storage.session.SessionWithEventsRecord

internal fun MwaSession.toEntity(): SessionEntity = SessionEntity(
    sessionId = id,
    startedAtEpochMillis = startedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    dappIdentityName = dappIdentityName,
    cluster = cluster,
    closeReason = closeReason?.name,
    associationMode = associationMode.name,
    identityVerificationState = identityVerificationState.name,
)

internal fun SessionEntity.toDomain(): MwaSession {
    require(cluster == "solana:devnet") { "Unsupported persisted cluster: $cluster" }
    return MwaSession(
        id = sessionId,
        startedAtEpochMillis = startedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        dappIdentityName = dappIdentityName,
        closeReason = closeReason?.let(SessionCloseReason::valueOf),
        associationMode = AssociationMode.valueOf(associationMode),
        identityVerificationState = DappVerificationState.valueOf(identityVerificationState),
    )
}

internal fun ProtocolEvidence.toEntity(): ProtocolEventEntity {
    val safeRequest = DiagnosticSanitizer.sanitizeFields(requestSummary)
    val safeResponse = DiagnosticSanitizer.sanitizeFields(responseSummary)
    val safeCapability = capabilityContext?.let(DiagnosticSanitizer::sanitizeFields)
    return ProtocolEventEntity(
        eventId = eventId,
        sessionId = sessionId,
        sequence = sequence,
        method = method.name,
        startedAtEpochMillis = startedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        outcome = outcome.name,
        protocolErrorCode = protocolErrorCode,
        failureSource = failureSource.name,
        injectedFaultId = injectedFaultId,
        requestSummaryJson = SafeSummaryJson.encode(safeRequest),
        responseSummaryJson = SafeSummaryJson.encode(safeResponse),
        capabilityContextJson = safeCapability?.let(SafeSummaryJson::encode),
    )
}

internal fun ProtocolEventEntity.toDomain(): ProtocolEvidence = ProtocolEvidence(
    sessionId = sessionId,
    eventId = eventId,
    sequence = sequence,
    method = ProtocolMethod.valueOf(method),
    startedAtEpochMillis = startedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    outcome = ProtocolOutcome.valueOf(outcome),
    protocolErrorCode = protocolErrorCode,
    failureSource = ProtocolFailureSource.valueOf(failureSource),
    requestSummary = SafeSummaryJson.decode(requestSummaryJson),
    responseSummary = SafeSummaryJson.decode(responseSummaryJson),
    injectedFaultId = injectedFaultId,
    capabilityContext = capabilityContextJson?.let(SafeSummaryJson::decode),
)

internal fun SessionWithEventsRecord.toDomain(): SessionSummary = SessionSummary(
    session = session.toDomain(),
    events = events.map(ProtocolEventEntity::toDomain),
)
