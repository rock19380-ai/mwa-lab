package dev.mwalab.democlient

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

enum class Phase5AcceptanceScenario(
    val kind: Phase5TransactionKind,
    val reject: Boolean,
) {
    GOOD_PASS_APPROVE(Phase5TransactionKind.GOOD_MEMO, false),
    BAD_FAIL_APPROVE(Phase5TransactionKind.BAD_SYSTEM_OPCODE, false),
    GOOD_PASS_REJECT(Phase5TransactionKind.GOOD_MEMO, true);

    companion object {
        fun fromWireName(value: String?) = entries.firstOrNull { it.name == value }
    }
}

/** This result proves the parent wire response only. A separate UI/Room check must prove simulation. */
data class Phase5AcceptanceResult(
    val scenario: Phase5AcceptanceScenario,
    val fingerprintSha256: String,
    val wireLength: Int,
    val feePayer: String,
    val signatureVerified: Boolean,
    val protocolErrorCode: Int?,
    val submittedTransactions: Int = 0,
)

class Phase5AcceptanceRunner(context: Context) {
    private val appContext = context.applicationContext
    private val devnet = DemoDevnetRpc()

    fun run(scenario: Phase5AcceptanceScenario): Phase5AcceptanceResult {
        val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        try {
            val implicit = LocalAssociationIntentCreator.createAssociationIntent(null, association.port, association.session)
            check(appContext.packageManager.queryIntentActivities(implicit, 0).any {
                it.activityInfo.packageName == WALLET_PACKAGE
            }) { "MWA Lab wallet endpoint is not discoverable" }
            check(appContext.packageName != WALLET_PACKAGE)
            appContext.startActivity(Intent(implicit).setPackage(WALLET_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val client = association.start().get(30, TimeUnit.SECONDS)
            val authorization = client.authorize(Uri.parse("https://phase5-demo-client.invalid"),
                Uri.parse("icon.png"), Phase4AcceptanceRunner.CLIENT_IDENTITY_NAME,
                ProtocolContract.CHAIN_SOLANA_DEVNET, null, null, null, null).get(10, TimeUnit.SECONDS)
            check(authorization.accounts.size == 1)
            val payer = authorization.accounts.single().publicKey
            val blockhash = devnet.getLatestBlockhash().bytes
            val unsigned = Phase5TransactionFactory.build(scenario.kind, payer, blockhash)
            val fee = devnet.getFeeForMessage(unsigned.message)
            val balance = devnet.getBalance(payer)
            if (balance < fee) {
                runCatching { client.deauthorize(authorization.authToken).get(10, TimeUnit.SECONDS) }
                throw Phase2FundingRequiredException(Base58.encodeToString(payer), balance, fee)
            }
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(unsigned.transaction)
                .joinToString("") { "%02x".format(it) }
            var verified = false
            var errorCode: Int? = null
            if (scenario.reject) {
                val failure = runCatching {
                    client.signTransactions(arrayOf(unsigned.transaction)).get(120, TimeUnit.SECONDS)
                }.exceptionOrNull() ?: error("Expected explicit rejection, but signing succeeded")
                val cause = if (failure is ExecutionException && failure.cause != null) failure.cause!! else failure
                check(cause is JsonRpc20Client.JsonRpc20RemoteException &&
                    cause.code == ProtocolContract.ERROR_NOT_SIGNED) {
                    "Expected authoritative ERROR_NOT_SIGNED after user rejection"
                }
                errorCode = ProtocolContract.ERROR_NOT_SIGNED
            } else {
                val signed = client.signTransactions(arrayOf(unsigned.transaction)).get(120, TimeUnit.SECONDS)
                check(signed.signedPayloads.size == 1)
                val signature = DemoLegacyTransactionFactory.primarySignature(
                    signed.signedPayloads.single(), unsigned.message)
                val verifier = Ed25519Signer()
                verifier.init(false, Ed25519PublicKeyParameters(payer, 0))
                verifier.update(unsigned.message, 0, unsigned.message.size)
                check(verifier.verifySignature(signature)) { "Returned signature failed verification" }
                verified = true
            }
            client.deauthorize(authorization.authToken).get(10, TimeUnit.SECONDS)
            return Phase5AcceptanceResult(scenario, fingerprint, unsigned.transaction.size,
                Base58.encodeToString(payer), verified, errorCode)
        } finally {
            runCatching { association.close().get(10, TimeUnit.SECONDS) }
        }
    }

    companion object {
        private const val WALLET_PACKAGE = "dev.mwalab"
    }
}
