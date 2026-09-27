package dev.mwalab.democlient

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.funkatronics.encoders.Base58
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
    SIGN_TRANSACTION_APPROVE("SIGN_TRANSACTION_APPROVE"),
    SIGN_AND_SEND_APPROVE("SIGN_AND_SEND_APPROVE"),
    SIGN_AND_SEND_REJECT("SIGN_AND_SEND_REJECT"),
    REVOKED_AUTH_REJECT("REVOKED_AUTH_REJECT"),
    SINGLE_OUTSTANDING_REQUEST_ISOLATION("SINGLE_OUTSTANDING_REQUEST_ISOLATION"),
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

class Phase2FundingRequiredException(
    val addressBase58: String,
    val balanceLamports: Long,
    val requiredFeeLamports: Long,
) : IllegalStateException(
    "LAB_IDENTITY_NEEDS_DEVNET_SOL address=$addressBase58 " +
        "balance_lamports=$balanceLamports required_fee_lamports=$requiredFeeLamports",
)

class Phase2AcceptanceRunner(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val devnetRpc = DemoDevnetRpc()

    fun run(scenario: Phase2AcceptanceScenario): Phase2AcceptanceResult = when (scenario) {
        Phase2AcceptanceScenario.REAUTHORIZE -> runReauthorizationAcrossSessions()
        Phase2AcceptanceScenario.SIGN_MESSAGE_APPROVE -> runSignMessage(expectRejection = false)
        Phase2AcceptanceScenario.SIGN_MESSAGE_REJECT -> runSignMessage(expectRejection = true)
        Phase2AcceptanceScenario.SIGN_TRANSACTION_APPROVE -> runSignTransaction()
        Phase2AcceptanceScenario.SIGN_AND_SEND_APPROVE -> runSignAndSend(expectRejection = false)
        Phase2AcceptanceScenario.SIGN_AND_SEND_REJECT -> runSignAndSend(expectRejection = true)
        Phase2AcceptanceScenario.REVOKED_AUTH_REJECT -> runRevokedAuthorizationReject()
        Phase2AcceptanceScenario.SINGLE_OUTSTANDING_REQUEST_ISOLATION -> runSingleOutstandingRequestIsolation()
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
            val authorization = authorize(session)
            val publicKey = authorization.accounts.single().publicKey
            val message = MESSAGE_TO_SIGN.copyOf()

            if (expectRejection) {
                val failure = runCatching {
                    session.client.signMessagesDetached(
                        arrayOf(message),
                        arrayOf(publicKey),
                    ).get(120, TimeUnit.SECONDS)
                }.exceptionOrNull() ?: error("Expected ERROR_NOT_SIGNED but signing succeeded")

                assertErrorNotSigned(failure)
                bestEffortDeauthorize(session, authorization.authToken)
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

            check(result.messages.size == 1) { "Expected exactly one signed message" }
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

    private fun runRevokedAuthorizationReject(): Phase2AcceptanceResult {
        val first = connect()
        val authorization = try {
            authorize(first)
        } catch (t: Throwable) {
            first.close()
            throw t
        }
        val publicKey = authorization.accounts.single().publicKey

        try {
            first.client.deauthorize(authorization.authToken)
                .get(10, TimeUnit.SECONDS)

            val sameSessionFailure = runCatching {
                first.client.signMessagesDetached(
                    arrayOf(REVOKED_AUTH_MESSAGE),
                    arrayOf(publicKey),
                ).get(10, TimeUnit.SECONDS)
            }.exceptionOrNull() ?: error(
                "Expected revoked current-session authorization to reject signing",
            )
            assertErrorAuthorizationFailed(sameSessionFailure)
        } finally {
            first.close()
        }

        val second = connect()
        try {
            val reauthorizationFailure = runCatching {
                second.client.reauthorize(
                    IDENTITY_URI,
                    ICON_URI,
                    CLIENT_IDENTITY_NAME,
                    authorization.authToken,
                ).get(10, TimeUnit.SECONDS)
            }.exceptionOrNull() ?: error(
                "Expected revoked auth token to fail cross-session reauthorization",
            )
            assertErrorAuthorizationFailed(reauthorizationFailure)
        } finally {
            second.close()
        }

        return Phase2AcceptanceResult(
            scenario = Phase2AcceptanceScenario.REVOKED_AUTH_REJECT,
            passed = true,
            summary =
                "deauthorized_state_rejected_signing_and_revoked_token_rejected_cross_session_reauthorization",
        )
    }

    private fun runSingleOutstandingRequestIsolation(): Phase2AcceptanceResult {
        val session = connect()
        try {
            val authorization = authorize(session)
            val publicKey = authorization.accounts.single().publicKey
            val firstMessage = CONCURRENT_MESSAGE_A.copyOf()
            val secondMessage = CONCURRENT_MESSAGE_B.copyOf()

            val firstFuture = session.client.signMessagesDetached(
                arrayOf(firstMessage),
                arrayOf(publicKey),
            )

            val secondFailure = runCatching {
                session.client.signMessagesDetached(
                    arrayOf(secondMessage),
                    arrayOf(publicKey),
                )
            }.exceptionOrNull() ?: error(
                "Expected clientlib to reject a second outstanding request on one association",
            )
            check(secondFailure is UnsupportedOperationException) {
                "Expected UnsupportedOperationException for a second outstanding request"
            }
            check(secondFailure.message == "Only a single request may be outstanding") {
                "Unexpected clientlib single-outstanding-request rejection"
            }

            val signedResult = firstFuture.get(120, TimeUnit.SECONDS)
            check(signedResult.messages.size == 1) {
                "Expected exactly one signed message from the outstanding request"
            }
            val signed = signedResult.messages.single()
            check(signed.message.contentEquals(firstMessage)) {
                "Outstanding signing response crossed request payload boundaries"
            }
            check(!signed.message.contentEquals(secondMessage)) {
                "Locally rejected request payload appeared in the wallet response"
            }
            check(signed.signatures.size == 1 && signed.addresses.size == 1) {
                "Expected exactly one signature/address pair for the outstanding request"
            }
            check(signed.addresses.single().contentEquals(publicKey)) {
                "Outstanding signing response crossed authorization address boundaries"
            }
            check(signed.signatures.single().size == ED25519_SIGNATURE_BYTES) {
                "Expected a 64-byte Ed25519 signature for the outstanding request"
            }
            check(verifyEd25519(publicKey, firstMessage, signed.signatures.single())) {
                "Outstanding request signature failed verification against its own payload"
            }
            check(!verifyEd25519(publicKey, secondMessage, signed.signatures.single())) {
                "Outstanding request signature unexpectedly verifies against rejected payload"
            }

            session.client.deauthorize(authorization.authToken)
                .get(10, TimeUnit.SECONDS)

            return Phase2AcceptanceResult(
                scenario = Phase2AcceptanceScenario.SINGLE_OUTSTANDING_REQUEST_ISOLATION,
                passed = true,
                summary =
                    "clientlib_single_outstanding_request_serialized_and_wallet_result_remained_payload_isolated",
            )
        } finally {
            session.close()
        }
    }

    private fun runSignTransaction(): Phase2AcceptanceResult {
        val session = connect()
        try {
            val authorization = authorize(session)
            val publicKey = authorization.accounts.single().publicKey
            val blockhash = devnetRpc.getLatestBlockhash()
            val unsigned = DemoLegacyTransactionFactory.memoTransaction(
                feePayer = publicKey,
                recentBlockhash = blockhash.bytes,
            )

            val result = session.client.signTransactions(arrayOf(unsigned.transaction))
                .get(120, TimeUnit.SECONDS)
            check(result.signedPayloads.size == 1) {
                "Expected exactly one signed transaction"
            }
            val signedTransaction = result.signedPayloads.single()
            val signature = DemoLegacyTransactionFactory.primarySignature(
                signedTransaction = signedTransaction,
                expectedMessage = unsigned.message,
            )
            check(signature.any { it != 0.toByte() }) { "Wallet returned an empty transaction signature" }
            check(verifyEd25519(publicKey, unsigned.message, signature)) {
                "Returned transaction signature failed Ed25519 verification"
            }

            session.client.deauthorize(authorization.authToken)
                .get(10, TimeUnit.SECONDS)

            return Phase2AcceptanceResult(
                scenario = Phase2AcceptanceScenario.SIGN_TRANSACTION_APPROVE,
                passed = true,
                summary = "approved_legacy_transaction_signature_verified_with_fresh_devnet_blockhash",
            )
        } finally {
            session.close()
        }
    }

    private fun runSignAndSend(expectRejection: Boolean): Phase2AcceptanceResult {
        val session = connect()
        try {
            val authorization = authorize(session)
            val publicKey = authorization.accounts.single().publicKey
            val blockhash = devnetRpc.getLatestBlockhash()
            val unsigned = DemoLegacyTransactionFactory.memoTransaction(
                feePayer = publicKey,
                recentBlockhash = blockhash.bytes,
            )

            if (expectRejection) {
                val failure = runCatching {
                    session.client.signAndSendTransactions(
                        arrayOf(unsigned.transaction),
                        null,
                        "confirmed",
                        false,
                        3,
                        false,
                    ).get(120, TimeUnit.SECONDS)
                }.exceptionOrNull() ?: error("Expected ERROR_NOT_SIGNED but sign-and-send succeeded")

                assertErrorNotSigned(failure)
                bestEffortDeauthorize(session, authorization.authToken)
                return Phase2AcceptanceResult(
                    scenario = Phase2AcceptanceScenario.SIGN_AND_SEND_REJECT,
                    passed = true,
                    summary = "user_rejection_returned_error_not_signed_before_submission",
                )
            }

            val fee = devnetRpc.getFeeForMessage(unsigned.message)
            val balance = devnetRpc.getBalance(publicKey)
            if (balance < fee) {
                bestEffortDeauthorize(session, authorization.authToken)
                throw Phase2FundingRequiredException(
                    addressBase58 = Base58.encodeToString(publicKey),
                    balanceLamports = balance,
                    requiredFeeLamports = fee,
                )
            }

            val result = session.client.signAndSendTransactions(
                arrayOf(unsigned.transaction),
                null,
                "confirmed",
                false,
                3,
                false,
            ).get(120, TimeUnit.SECONDS)

            check(result.signatures.size == 1) { "Expected exactly one submitted signature" }
            val signature = result.signatures.single()
            check(signature.size == ED25519_SIGNATURE_BYTES) {
                "Expected a 64-byte Solana transaction signature"
            }
            check(verifyEd25519(publicKey, unsigned.message, signature)) {
                "Returned sign-and-send signature failed Ed25519 verification"
            }

            session.client.deauthorize(authorization.authToken)
                .get(10, TimeUnit.SECONDS)

            return Phase2AcceptanceResult(
                scenario = Phase2AcceptanceScenario.SIGN_AND_SEND_APPROVE,
                passed = true,
                summary = "approved_memo_transaction_submitted_and_confirmed_on_devnet",
            )
        } finally {
            session.close()
        }
    }

    private fun authorize(session: ConnectedSession): MobileWalletAdapterClient.AuthorizationResult {
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
            "Expected exactly one Lab account for Phase 2 acceptance"
        }
        return authorization
    }

    private fun assertErrorNotSigned(failure: Throwable) {
        assertRemoteErrorCode(
            failure = failure,
            expectedCode = ProtocolContract.ERROR_NOT_SIGNED,
            expectedName = "ERROR_NOT_SIGNED",
        )
    }

    private fun assertErrorAuthorizationFailed(failure: Throwable) {
        assertRemoteErrorCode(
            failure = failure,
            expectedCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
            expectedName = "ERROR_AUTHORIZATION_FAILED",
        )
    }

    private fun assertRemoteErrorCode(
        failure: Throwable,
        expectedCode: Int,
        expectedName: String,
    ) {
        val cause = unwrapExecutionFailure(failure)
        check(
            cause is JsonRpc20Client.JsonRpc20RemoteException &&
                cause.code == expectedCode,
        ) {
            "Expected $expectedName ($expectedCode), got ${cause::class.java.name}"
        }
    }

    private fun bestEffortDeauthorize(
        session: ConnectedSession,
        authToken: String,
    ) {
        runCatching {
            session.client.deauthorize(authToken).get(10, TimeUnit.SECONDS)
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
        private val REVOKED_AUTH_MESSAGE =
            "MWA Lab revoked authorization must not sign".encodeToByteArray()
        private val CONCURRENT_MESSAGE_A =
            "MWA Lab concurrent request A".encodeToByteArray()
        private val CONCURRENT_MESSAGE_B =
            "MWA Lab concurrent request B".encodeToByteArray()
    }
}
