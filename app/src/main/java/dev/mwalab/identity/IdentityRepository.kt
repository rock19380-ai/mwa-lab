package dev.mwalab.identity

interface IdentityRepository {
    suspend fun getOrCreate(): TestEndpointIdentity

    /**
     * Invalidates the currently authoritative encrypted identity and creates
     * a new Devnet-only test identity. No seed/private-key export is provided.
     */
    suspend fun reset(): TestEndpointIdentity
}
