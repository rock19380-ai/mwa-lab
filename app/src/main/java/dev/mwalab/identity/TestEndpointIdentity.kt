package dev.mwalab.identity

import com.solana.mobilewalletadapter.common.ProtocolContract

class TestEndpointIdentity internal constructor(
    publicKey: ByteArray,
    val displayAddress: String,
    val displayAddressFormat: String = "base58",
    val chain: String = ProtocolContract.CHAIN_SOLANA_DEVNET,
    val label: String = "MWA Lab Devnet Test Identity",
) {
    private val publicKeyBytes = publicKey.copyOf()

    fun publicKeyBytes(): ByteArray = publicKeyBytes.copyOf()
}
