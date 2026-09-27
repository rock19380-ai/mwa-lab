package dev.mwalab.signing

import dev.mwalab.identity.TestEndpointIdentity

interface LabSigningService {
    suspend fun publicIdentity(): TestEndpointIdentity
    suspend fun sign(message: ByteArray): ByteArray
}
