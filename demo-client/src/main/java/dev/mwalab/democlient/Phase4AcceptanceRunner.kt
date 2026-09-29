package dev.mwalab.democlient

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

enum class Phase4AcceptanceScenario {
    SYSTEM_TRANSFER, UNKNOWN_PROGRAM, V0_REJECT;
    companion object {
        fun fromWireName(value: String?) = entries.firstOrNull { it.name == value }
    }
}

/** Safe original-payload metadata only; no signed bytes, signature, or authorization token. */
data class Phase4AcceptanceResult(
    val scenario: Phase4AcceptanceScenario,
    val fingerprintSha256: String,
    val wireLength: Int,
    val feePayer: String,
    val recentBlockhash: String,
    val signatureVerified: Boolean,
    val protocolErrorCode: Int?,
)

/** Separate Phase 4 signTransactions exercise. It never submits, funds, or simulates. */
class Phase4AcceptanceRunner(context: Context) {
    private val appContext = context.applicationContext

    fun run(scenario: Phase4AcceptanceScenario): Phase4AcceptanceResult {
        val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        try {
            val implicit = LocalAssociationIntentCreator.createAssociationIntent(null, association.port, association.session)
            check(appContext.packageManager.queryIntentActivities(implicit, 0).any {
                it.activityInfo.packageName == WALLET_PACKAGE
            }) { "MWA Lab wallet endpoint is not discoverable" }
            check(appContext.packageName != WALLET_PACKAGE)
            appContext.startActivity(Intent(implicit).setPackage(WALLET_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val client = association.start().get(30, TimeUnit.SECONDS)
            val authorization = client.authorize(Uri.parse("https://phase4-demo-client.invalid"), Uri.parse("icon.png"),
                CLIENT_IDENTITY_NAME, ProtocolContract.CHAIN_SOLANA_DEVNET, null, null, null, null)
                .get(10, TimeUnit.SECONDS)
            check(authorization.accounts.size == 1)
            val publicKey = authorization.accounts.single().publicKey
            val blockhash = DemoDevnetRpc().getLatestBlockhash().bytes
            val unsigned = Phase4TransactionFactory.build(scenario, publicKey, blockhash)
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(unsigned.transaction)
                .joinToString("") { "%02x".format(it) }
            var verified = false
            var errorCode: Int? = null
            if (scenario == Phase4AcceptanceScenario.V0_REJECT) {
                val failure = runCatching {
                    client.signTransactions(arrayOf(unsigned.transaction)).get(120, TimeUnit.SECONDS)
                }.exceptionOrNull() ?: error("v0 signing unexpectedly succeeded")
                val cause = if (failure is ExecutionException && failure.cause != null) failure.cause!! else failure
                check(cause is MobileWalletAdapterClient.InvalidPayloadsException &&
                    cause.validPayloads.contentEquals(booleanArrayOf(false))) { "Expected authoritative invalid-payload rejection" }
                errorCode = ProtocolContract.ERROR_INVALID_PAYLOADS
            } else {
                val signed = client.signTransactions(arrayOf(unsigned.transaction)).get(120, TimeUnit.SECONDS)
                check(signed.signedPayloads.size == 1)
                val signature = DemoLegacyTransactionFactory.primarySignature(signed.signedPayloads.single(), unsigned.message)
                val verifier = Ed25519Signer()
                verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
                verifier.update(unsigned.message, 0, unsigned.message.size)
                check(verifier.verifySignature(signature)) { "Returned transaction signature failed verification" }
                verified = true
            }
            client.deauthorize(authorization.authToken).get(10, TimeUnit.SECONDS)
            return Phase4AcceptanceResult(scenario, fingerprint, unsigned.transaction.size,
                Base58.encodeToString(publicKey), Base58.encodeToString(blockhash), verified, errorCode)
        } finally {
            runCatching { association.close().get(10, TimeUnit.SECONDS) }
        }
    }

    companion object {
        private const val WALLET_PACKAGE = "dev.mwalab"
        const val CLIENT_IDENTITY_NAME = "MWA Lab Demo Client — FOR TESTING ONLY"
    }
}
