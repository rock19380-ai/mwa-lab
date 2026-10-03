package dev.mwalab.rpc

/**
 * Unit-test-only adapter for Test Wallet cases that exercise wallet utility RPCs.
 *
 * The pre-existing simulation/sign-and-send RPC contract remains abstract in
 * production. Tests that do not exercise those methods fail closed here rather
 * than forcing production defaults or weakening the gateway interface.
 */
internal abstract class WalletUtilityTestRpcGateway : DevnetRpcGateway {
    override suspend fun simulateTransaction(
        transaction: ByteArray,
        options: DevnetSimulationOptions,
    ): DevnetRpcResult<SimulationRpcValue> = DevnetRpcResult.MalformedResponse

    override suspend fun isBlockhashValid(
        blockhash: ByteArray,
        minContextSlot: Int?,
    ): DevnetRpcResult<Boolean> = DevnetRpcResult.MalformedResponse

    override suspend fun sendTransaction(
        signedTransaction: ByteArray,
        options: DevnetSendOptions,
    ): DevnetRpcResult<ByteArray> = DevnetRpcResult.MalformedResponse

    override suspend fun awaitCommitment(
        signature: ByteArray,
        commitment: String,
        timeoutMillis: Long,
    ): DevnetRpcResult<Boolean> = DevnetRpcResult.MalformedResponse
}
