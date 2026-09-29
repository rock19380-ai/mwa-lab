package dev.mwalab.storage.capabilities

import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.CapabilitySnapshotSource

internal fun CapabilitySnapshot.toEntity(): CapabilitySnapshotEntity = CapabilitySnapshotEntity(
    sessionId = sessionId,
    capturedAtEpochMillis = capturedAtEpochMillis,
    source = source.name,
    maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest,
    maxMessagesPerSigningRequest = maxMessagesPerSigningRequest,
    supportedVersionsJson = CapabilityListJson.encode(supportedTransactionVersions),
    optionalFeaturesJson = CapabilityListJson.encode(optionalFeatures),
)

internal fun CapabilitySnapshotEntity.toDomain(): CapabilitySnapshot = CapabilitySnapshot(
    sessionId = sessionId,
    capturedAtEpochMillis = capturedAtEpochMillis,
    source = CapabilitySnapshotSource.entries.singleOrNull { it.name == source }
        ?: throw IllegalArgumentException("Invalid persisted capability provenance"),
    maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest,
    maxMessagesPerSigningRequest = maxMessagesPerSigningRequest,
    supportedTransactionVersions = CapabilityListJson.decode(supportedVersionsJson),
    optionalFeatures = CapabilityListJson.decode(optionalFeaturesJson),
)
