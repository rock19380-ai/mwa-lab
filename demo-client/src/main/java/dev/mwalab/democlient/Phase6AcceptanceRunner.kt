package dev.mwalab.democlient

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Client-side expectations only. MWA Lab's ProtocolEvent remains wallet-side evidence. */
enum class Phase6AcceptanceScenario(val expectedCode: Int?, val operation: Operation) {
    AUTH_REJECT(ProtocolContract.ERROR_AUTHORIZATION_FAILED, Operation.AUTHORIZE),
    UNSUPPORTED_CHAIN(ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED, Operation.AUTHORIZE),
    SIGN_REJECT(ProtocolContract.ERROR_NOT_SIGNED, Operation.SIGN_MESSAGE),
    DELAY_5S(null, Operation.SIGN_MESSAGE),
    INVALID_PAYLOAD(ProtocolContract.ERROR_INVALID_PAYLOADS, Operation.SIGN_MESSAGE),
    TOO_MANY_PAYLOADS(ProtocolContract.ERROR_TOO_MANY_PAYLOADS, Operation.SIGN_MESSAGE),
    STALE_BLOCKHASH(ProtocolContract.ERROR_INVALID_PAYLOADS, Operation.SIGN_TRANSACTION),
    RPC_UNAVAILABLE(ProtocolContract.ERROR_NOT_SUBMITTED, Operation.SIGN_AND_SEND),
    SUBMISSION_FAILURE(ProtocolContract.ERROR_NOT_SUBMITTED, Operation.SIGN_AND_SEND),
    NORMAL_REGRESSION(null, Operation.SIGN_MESSAGE);

    enum class Operation { AUTHORIZE, SIGN_MESSAGE, SIGN_TRANSACTION, SIGN_AND_SEND }
    companion object {
        fun fromWireName(value: String?) = entries.firstOrNull { it.name == value }
    }
}

data class Phase6AcceptanceResult(
    val scenario: Phase6AcceptanceScenario,
    val requestedOperation: Phase6AcceptanceScenario.Operation,
    val receivedProtocolCode: Int?,
    val elapsedMillis: Long,
    val signatureVerified: Boolean,
)

/** The developer selects the matching fault in the wallet before launching this scenario. */
class Phase6AcceptanceRunner(context: Context) {
    private val appContext = context.applicationContext

    fun run(scenario: Phase6AcceptanceScenario): Phase6AcceptanceResult {
        val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        try {
            val implicit = LocalAssociationIntentCreator.createAssociationIntent(null, association.port, association.session)
            check(appContext.packageName != WALLET_PACKAGE)
            check(appContext.packageManager.queryIntentActivities(implicit, 0).any {
                it.activityInfo.packageName == WALLET_PACKAGE
            }) { "MWA Lab wallet endpoint is not discoverable" }
            appContext.startActivity(Intent(implicit).setPackage(WALLET_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val client = association.start().get(30, TimeUnit.SECONDS)
            val started = System.nanoTime()
            val authFuture = client.authorize(Uri.parse("https://phase6-demo-client.invalid"),
                Uri.parse("icon.png"), Phase4AcceptanceRunner.CLIENT_IDENTITY_NAME,
                ProtocolContract.CHAIN_SOLANA_DEVNET, null, null, null, null)
            if (scenario.operation == Phase6AcceptanceScenario.Operation.AUTHORIZE) {
                val code = expectProtocolError(authFuture, checkNotNull(scenario.expectedCode))
                return Phase6AcceptanceResult(scenario, scenario.operation, code, elapsed(started), false)
            }
            val authorization = authFuture.get(10, TimeUnit.SECONDS)
            check(authorization.accounts.size == 1)
            val publicKey = authorization.accounts.single().publicKey
            try {
                val requestStarted = System.nanoTime()
                when (scenario.operation) {
                    Phase6AcceptanceScenario.Operation.SIGN_MESSAGE -> {
                        val message = "MWA Lab Phase 6 Devnet test".encodeToByteArray()
                        val future = client.signMessagesDetached(arrayOf(message), arrayOf(publicKey))
                        if (scenario.expectedCode != null) {
                            val code = expectProtocolError(future, scenario.expectedCode)
                            return Phase6AcceptanceResult(scenario, scenario.operation, code, elapsed(requestStarted), false)
                        }
                        val result = future.get(120, TimeUnit.SECONDS)
                        check(result.messages.size == 1)
                        val signed = result.messages.single()
                        check(signed.message.contentEquals(message))
                        check(signed.addresses.size == 1 && signed.addresses.single().contentEquals(publicKey))
                        check(signed.signatures.size == 1)
                        val verifier = Ed25519Signer()
                        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
                        verifier.update(message, 0, message.size)
                        check(verifier.verifySignature(signed.signatures.single()))
                        val duration = elapsed(requestStarted)
                        if (scenario == Phase6AcceptanceScenario.DELAY_5S) {
                            check(duration >= 4_900) { "Expected a five-second delay before normal signing" }
                        }
                        return Phase6AcceptanceResult(scenario, scenario.operation, null, duration, true)
                    }
                    Phase6AcceptanceScenario.Operation.SIGN_TRANSACTION,
                    Phase6AcceptanceScenario.Operation.SIGN_AND_SEND -> {
                        val blockhash = DemoDevnetRpc().getLatestBlockhash().bytes
                        val unsigned = Phase5TransactionFactory.build(
                            Phase5TransactionKind.GOOD_MEMO, publicKey, blockhash)
                        val future: Future<*> = if (scenario.operation == Phase6AcceptanceScenario.Operation.SIGN_TRANSACTION)
                            client.signTransactions(arrayOf(unsigned.transaction))
                        else client.signAndSendTransactions(arrayOf(unsigned.transaction), null,
                            "confirmed", false, 3, false)
                        val code = expectProtocolError(future, checkNotNull(scenario.expectedCode))
                        return Phase6AcceptanceResult(scenario, scenario.operation, code, elapsed(requestStarted), false)
                    }
                    Phase6AcceptanceScenario.Operation.AUTHORIZE -> error("Already handled")
                }
            } finally {
                runCatching { client.deauthorize(authorization.authToken).get(10, TimeUnit.SECONDS) }
            }
        } finally {
            runCatching { association.close().get(10, TimeUnit.SECONDS) }
        }
    }

    private fun expectProtocolError(future: Future<*>, expected: Int): Int {
        val failure = try {
            future.get(120, TimeUnit.SECONDS)
            error("Expected protocol error $expected but operation succeeded")
        } catch (error: ExecutionException) { error.cause ?: error }
        val actual = when (failure) {
            is MobileWalletAdapterClient.InvalidPayloadsException -> {
                check(failure.validPayloads.isNotEmpty() && !failure.validPayloads.any { it })
                ProtocolContract.ERROR_INVALID_PAYLOADS
            }
            is JsonRpc20Client.JsonRpc20RemoteException -> failure.code
            else -> throw failure
        }
        check(actual == expected) { "Expected protocol error $expected, received $actual" }
        return actual
    }

    private fun elapsed(startedNanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)

    companion object { private const val WALLET_PACKAGE = "dev.mwalab" }
}
