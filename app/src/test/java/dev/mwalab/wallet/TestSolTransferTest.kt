package dev.mwalab.wallet

import com.funkatronics.encoders.Base58
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSendOptions
import dev.mwalab.rpc.LatestBlockhash
import dev.mwalab.rpc.WalletUtilityTestRpcGateway
import dev.mwalab.signing.LabSigningService
import dev.mwalab.transaction.LegacyTransactionCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSolTransferTest {
    private val from = ByteArray(32) { (it + 1).toByte() }
    private val to = ByteArray(32) { (it + 33).toByte() }
    private val fromAddress = Base58.encodeToString(from)
    private val toAddress = Base58.encodeToString(to)

    @Test
    fun recipientAndAmountParsingAreStrictAndCanonical() {
        assertTrue(SolanaPublicKeyParser.parse(toAddress)!!.contentEquals(to))
        assertEquals(null, SolanaPublicKeyParser.parse("https://example.com"))
        assertEquals(null, SolanaPublicKeyParser.parse("0OIl"))
        assertEquals(null, SolanaPublicKeyParser.parse(Base58.encodeToString(ByteArray(31) { 1 })))

        assertEquals(10_000_000L, parseSolAmountToLamports("0.01"))
        assertEquals(1L, parseSolAmountToLamports("0.000000001"))
        assertEquals(null, parseSolAmountToLamports("0"))
        assertEquals(null, parseSolAmountToLamports("-1"))
        assertEquals(null, parseSolAmountToLamports("1e-3"))
        assertEquals(null, parseSolAmountToLamports("0.0000000001"))
        assertEquals(null, parseSolAmountToLamports("999999999999999999999"))
    }

    @Test
    fun builderProducesOneSignerLegacySystemTransfer() {
        val blockhash = ByteArray(32) { 0x55 }
        val transaction = TestSolTransferBuilder.build(from, to, blockhash, 10_000_000L)
        val parsed = LegacyTransactionCodec.parseForSigner(transaction, from)
        assertEquals(1, parsed.signatureCount)
        assertTrue(parsed.recentBlockhash.contentEquals(blockhash))
        assertTrue(parsed.primarySignature().all { it == 0.toByte() })
        assertTrue(parsed.message.containsSubsequence(to))
        assertTrue(parsed.message.containsSubsequence(byteArrayOf(2, 0, 0, 0)))
    }

    @Test
    fun serviceRequiresBalanceReserveAndOnlyConfirmsOnCommitmentEvidence() = runTest {
        val signature = ByteArray(64) { 9 }
        val identity = object : IdentityRepository {
            override suspend fun getOrCreate() = TestEndpointIdentity(from, fromAddress)
            override suspend fun reset(): TestEndpointIdentity = error("reset must not be used")
        }
        val signer = object : LabSigningService {
            override suspend fun publicIdentity() = TestEndpointIdentity(from, fromAddress)
            override suspend fun sign(message: ByteArray): ByteArray = signature.copyOf()
        }
        var sent = false
        val gateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray) = DevnetRpcResult.Success(20_000_000L)
            override suspend fun getLatestBlockhash() = DevnetRpcResult.Success(
                LatestBlockhash(ByteArray(32) { 0x44 }, 999, 123),
            )
            override suspend fun sendTransaction(
                signedTransaction: ByteArray,
                options: DevnetSendOptions,
            ): DevnetRpcResult<ByteArray> {
                sent = true
                assertFalse(options.skipPreflight ?: true)
                assertEquals("confirmed", options.commitment)
                return DevnetRpcResult.Success(signature)
            }
            override suspend fun awaitCommitment(
                signature: ByteArray,
                commitment: String,
                timeoutMillis: Long,
            ) = DevnetRpcResult.Success(true)
        }
        val service = TestWalletSendService(identity, signer, gateway)
        val prepared = service.prepare(toAddress, "0.01")
        assertTrue(prepared is WalletSendPreparationResult.Ready)
        val transfer = (prepared as WalletSendPreparationResult.Ready).transfer
        assertEquals("0.01", transfer.review().amountSol)
        assertEquals(WalletSendSubmissionResult.Confirmed, service.submit(transfer))
        assertTrue(sent)

        val insufficient = TestWalletSendService(
            identity,
            signer,
            object : WalletUtilityTestRpcGateway() {
                override suspend fun getBalance(publicKey: ByteArray) =
                    DevnetRpcResult.Success(10_000_000L + MIN_SEND_FEE_RESERVE_LAMPORTS - 1)
            },
        ).prepare(toAddress, "0.01")
        assertEquals(
            WalletSendFailureReason.INSUFFICIENT_FUNDS,
            (insufficient as WalletSendPreparationResult.Failed).reason,
        )
    }

    @Test
    fun submissionAmbiguityAndSignatureMismatchAreNeverSuccess() = runTest {
        val signature = ByteArray(64) { 3 }
        val identity = object : IdentityRepository {
            override suspend fun getOrCreate() = TestEndpointIdentity(from, fromAddress)
            override suspend fun reset(): TestEndpointIdentity = error("reset must not be used")
        }
        val signer = object : LabSigningService {
            override suspend fun publicIdentity() = TestEndpointIdentity(from, fromAddress)
            override suspend fun sign(message: ByteArray) = signature.copyOf()
        }
        suspend fun prepared(gateway: WalletUtilityTestRpcGateway): PreparedTestSolTransfer {
            val result = TestWalletSendService(identity, signer, gateway).prepare(toAddress, "0.01")
            return (result as WalletSendPreparationResult.Ready).transfer
        }
        val unknownGateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray) = DevnetRpcResult.Success(100_000_000L)
            override suspend fun getLatestBlockhash() = DevnetRpcResult.Success(
                LatestBlockhash(ByteArray(32) { 0x22 }, 999, 10),
            )
            override suspend fun sendTransaction(signedTransaction: ByteArray, options: DevnetSendOptions) =
                DevnetRpcResult.Success(signature)
            override suspend fun awaitCommitment(signature: ByteArray, commitment: String, timeoutMillis: Long) =
                DevnetRpcResult.Success(false)
        }
        val transfer = prepared(unknownGateway)
        assertEquals(
            WalletSendSubmissionResult.SubmittedUnknown,
            TestWalletSendService(identity, signer, unknownGateway).submit(transfer),
        )

        val mismatchGateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray) = DevnetRpcResult.Success(100_000_000L)
            override suspend fun getLatestBlockhash() = DevnetRpcResult.Success(
                LatestBlockhash(ByteArray(32) { 0x22 }, 999, 10),
            )
            override suspend fun sendTransaction(signedTransaction: ByteArray, options: DevnetSendOptions) =
                DevnetRpcResult.Success(ByteArray(64) { 4 })
        }
        val mismatchTransfer = prepared(mismatchGateway)
        val mismatch = TestWalletSendService(identity, signer, mismatchGateway).submit(mismatchTransfer)
        assertEquals(WalletSendSubmissionResult.SubmittedUnknown, mismatch)

        val transportFailureGateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray) = DevnetRpcResult.Success(100_000_000L)
            override suspend fun getLatestBlockhash() = DevnetRpcResult.Success(
                LatestBlockhash(ByteArray(32) { 0x22 }, 999, 10),
            )
            override suspend fun sendTransaction(signedTransaction: ByteArray, options: DevnetSendOptions) =
                DevnetRpcResult.TransportFailure(dev.mwalab.rpc.TransportFailureReason.TIMEOUT)
        }
        val ambiguousTransfer = prepared(transportFailureGateway)
        assertEquals(
            WalletSendSubmissionResult.SubmittedUnknown,
            TestWalletSendService(identity, signer, transportFailureGateway).submit(ambiguousTransfer),
        )
    }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        if (needle.size > size) return false
        for (start in 0..size - needle.size) {
            var matches = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }
}
