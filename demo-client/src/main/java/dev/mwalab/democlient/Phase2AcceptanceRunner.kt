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
import java.util.concurrent.TimeUnit

enum class Phase2AcceptanceScenario(val wireName: String) {
    REAUTHORIZE("REAUTHORIZE"),
    SIGN_MESSAGE_APPROVE("SIGN_MESSAGE_APPROVE"),
    SIGN_MESSAGE_REJECT("SIGN_MESSAGE_REJECT"),
    ;

    companion object {
        fun fromWireName(value: String?): Phase2AcceptanceScenario? =
            entries.firstOrNull { it.wireName == value }
    }
}

data class Phase2AcceptanceResult(
    val scenario: Phase2AcceptanceScenario,
    val passed: Boolean,
    val summary: String,
)

class Phase2AcceptanceRunner(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun run(scenario: Phase2AcceptanceScenario): Phase2AcceptanceResult = when (scenario) {
        Phase2AcceptanceScenario.REAUTHORIZE -> runReauthorizationAcrossSessions()
        Phase2AcceptanceScenario.SIGN_MESSAGE_APPROVE -> runSignMessage(expectRejection = false)
        Phase2AcceptanceScenario.SIGN_MESSAGE_REJECT -> runSignMessage(expectRejection = true)
    }

    private fun runReauthorizationAcrossSessions(): Phase2AcceptanceResult {
        val first = connect()
        val firstAuthorization = try {
            first.client.authorize(
                IDENTITY_URI,
                ICON_URI,
                CLIENT_IDENTITY_NAME,
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)
        } finally {
            first.close()
        }

        check(firstAuthorization.accounts.size == 1) {
            "Expected exactly one Lab account during initial authorization"
        }
        val firstPublicKey = firstAuthorization.accounts.single().publicKey

        val second = connect()
        try {
            val reauthorization = second.client.reauthorize(
                IDENTITY_URI,
                ICON_URI,
                CLIENT_IDENTITY_NAME,
                firstAuthorization.authToken,
            ).get(10, TimeUnit.SECONDS)

            check(reauthorization.accounts.size == 1) {
                "Expected exactly one Lab account during reauthorization"
            }
            check(reauthorization.accounts.single().publicKey.contentEquals(firstPublicKey)) {
                "Reauthorization returned a different Lab identity"
            }

            second.client.deauthorize(reauthorization.authToken)
                .get(10, TimeUnit.SECONDS)
        } finally {
            second.close()
        }

        return Phase2AcceptanceResult(
            scenario = Phase2AcceptanceScenario.REAUTHORIZE,
            passed = true,
            summary = "existing_walletlib_authorization_reused_across_sessions",
        )
    }

    private fun runSignMessage(expectRejection: Boolean): Phase2AcceptanceResult {
        val session = connect()
        try {
            val authorization = session.client.authorize(
                IDENTITY_URI,
                ICON_URI,
                CLIENT_IDENTITY_NAME,
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)

            check(authorization.accounts.size == 1) {
                "Expected exactly one Lab account for message signing"
            }
            val publicKey = authorization.accounts.single().publicKey
            val message = MESSAGE_TO_SIGN.copyOf()

            if (expectRejection) {
                val failure = runCatching {
                    session.client.signMessagesDetached(
                        arrayOf(message),
                        arrayOf(publicKey),
                    ).get(120, TimeUnit.SECONDS)
                }.exceptionOrNull() ?: error("Expected ERROR_NOT_SIGNED but signing succeeded")

                val cause = unwrapExecutionFailure(failure)
                check(
                    cause is JsonRpc20Client.JsonRpc20RemoteException &&
                        cause.code == ProtocolContract.ERROR_NOT_SIGNED,
                ) {
                    "Expected ERROR_NOT_SIGNED (${ProtocolContract.ERROR_NOT_SIGNED}), got ${cause::class.java.name}"
                }

                runCatching {
                    session.client.deauthorize(authorization.authToken)
                        .get(10, TimeUnit.SECONDS)
                }

                return Phase2AcceptanceResult(
                    scenario = Phase2AcceptanceScenario.SIGN_MESSAGE_REJECT,
                    passed = true,
                    summary = "user_rejection_returned_error_not_signed",
                )
            }

            val result = session.client.signMessagesDetached(
                arrayOf(message),
                arrayOf(publicKey),
            ).get(120, TimeUnit.SECONDS)

            check(result.messages.size == 1) {
                "Expected exactly one signed message"
            }
            val signed = result.messages.single()
            check(signed.message.contentEquals(message)) {
                "Signed message payload differs from requested message"
            }
            check(signed.signatures.size == 1 && signed.addresses.size == 1) {
                "Expected exactly one signature/address pair"
            }
            check(signed.addresses.single().contentEquals(publicKey)) {
                "Signed-message address differs from authorized Lab account"
            }
            check(signed.signatures.single().size == ED25519_SIGNATURE_BYTES) {
                "Expected a 64-byte Ed25519 signature"
            }
            check(verifyEd25519(publicKey, message, signed.signatures.single())) {
                "Returned signature failed Ed25519 verification"
            }

            session.client.deauthorize(authorization.authToken)
                .get(10, TimeUnit.SECONDS)

            return Phase2AcceptanceResult(
                scenario = Phase2AcceptanceScenario.SIGN_MESSAGE_APPROVE,
                passed = true,
                summary = "approved_message_signature_verified",
            )
        } finally {
            session.close()
        }
    }

    private fun connect(): ConnectedSession {
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val implicitIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session,
        )

        val walletResolver = appContext.packageManager.queryIntentActivities(
            implicitIntent,
            0,
        ).firstOrNull { it.activityInfo.packageName == WALLET_PACKAGE }
            ?: error("MWA Lab wallet endpoint is not discoverable")

        check(walletResolver.activityInfo.packageName != appContext.packageName) {
            "Demo client and wallet endpoint must be different Android packages"
        }

        val walletIntent = Intent(implicitIntent)
            .setPackage(WALLET_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        appContext.startActivity(walletIntent)
        val client = localAssociation.start().get(30, TimeUnit.SECONDS)
        return ConnectedSession(localAssociation, client)
    }

    private fun verifyEd25519(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    private fun unwrapExecutionFailure(failure: Throwable): Throwable =
        if (failure is ExecutionException && failure.cause != null) {
            failure.cause!!
        } else {
            failure
        }

    private data class ConnectedSession(
        val association: LocalAssociationScenario,
        val client: MobileWalletAdapterClient,
    ) {
        fun close() {
            runCatching {
                association.close().get(10, TimeUnit.SECONDS)
            }
        }
    }

    companion object {
        private const val WALLET_PACKAGE = "dev.mwalab"
        private const val CLIENT_IDENTITY_NAME = "MWA Lab Demo Client — FOR TESTING ONLY"
        private const val ED25519_SIGNATURE_BYTES = 64
        private val IDENTITY_URI = Uri.parse("https://phase2-demo-client.invalid")
        private val ICON_URI = Uri.parse("icon.png")
        private val MESSAGE_TO_SIGN =
            "MWA Lab Phase 2 cross-app approval acceptance".encodeToByteArray()
    }
}
