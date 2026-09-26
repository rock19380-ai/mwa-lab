package dev.mwalab.mwa

import android.content.Context
import android.net.Uri
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.AuthorizeRequest
import com.solana.mobilewalletadapter.walletlib.scenario.DeauthorizedEvent
import com.solana.mobilewalletadapter.walletlib.scenario.LocalScenario
import com.solana.mobilewalletadapter.walletlib.scenario.ReauthorizeRequest
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario
import com.solana.mobilewalletadapter.walletlib.scenario.SignAndSendTransactionsRequest
import com.solana.mobilewalletadapter.walletlib.scenario.SignMessagesRequest
import com.solana.mobilewalletadapter.walletlib.scenario.SignTransactionsRequest
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.mwa.authorization.LabAuthorizationDecision
import dev.mwalab.mwa.authorization.LabAuthorizationPolicy
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidence
import dev.mwalab.mwa.evidence.MwaSessionEvidenceSink
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns exactly one wallet-side MWA scenario at a time.
 *
 * Phase 1 supports authorization only for the persistent MWA Lab Devnet test
 * identity. walletlib 2.0.7 remains authoritative for auth-token issue,
 * persistence, validation, reissue plumbing, and revocation.
 *
 * Full existing-auth-token/reauthorization behavior remains deferred to Phase 2.
 * Successful signing remains deferred; mandatory signing callbacks fail closed.
 */
class MwaSessionHost(
    context: Context,
    identityRepository: IdentityRepository =
        MwaLabComposition.identityRepository(context.applicationContext),
    private val evidenceSink: MwaSessionEvidenceSink = MwaSessionEvidenceStore,
    private val onSessionFinished: () -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val authorizationPolicy = LabAuthorizationPolicy(identityRepository)
    private val authorizationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scenario: Scenario? = null

    private val walletConfig = MobileWalletAdapterConfig(
        /* maxTransactionsPerSigningRequest = */ 0,
        /* maxMessagesPerSigningRequest = */ 0,
        arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION),
        /* noConnectionWarningTimeoutMs = */ LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
        /* supportedFeatures = */ emptyArray(),
    )

    private val authIssuerConfig = AuthIssuerConfig(AUTH_ISSUER_NAME)

    fun openAssociation(uri: Uri?): AssociationOpenResult {
        closeCurrentScenario(recordClose = false)

        if (uri == null) {
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "missing_uri")
            return AssociationOpenResult.Rejected(AssociationOpenResult.Reason.MISSING_URI)
        }

        if (uri.scheme != REQUIRED_SCHEME) {
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "unsupported_scheme")
            return AssociationOpenResult.Rejected(AssociationOpenResult.Reason.UNSUPPORTED_SCHEME)
        }

        val parsed = try {
            AssociationUri.parse(uri)
        } catch (_: Throwable) {
            null
        }

        if (parsed == null) {
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "invalid_association")
            return AssociationOpenResult.Rejected(
                AssociationOpenResult.Reason.INVALID_ASSOCIATION,
            )
        }

        if (parsed !is LocalAssociationUri) {
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "non_local_association")
            return AssociationOpenResult.Rejected(
                AssociationOpenResult.Reason.NON_LOCAL_ASSOCIATION,
            )
        }

        val candidate = try {
            parsed.createScenario(
                appContext,
                walletConfig,
                authIssuerConfig,
                callbacks,
            )
        } catch (_: Throwable) {
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "scenario_creation_failed")
            return AssociationOpenResult.Rejected(
                AssociationOpenResult.Reason.SCENARIO_CREATION_FAILED,
            )
        }

        synchronized(lock) {
            scenario = candidate
        }

        return try {
            record(MwaSessionEvent.ASSOCIATION_ACCEPTED, "local")
            record(MwaSessionEvent.SCENARIO_START_REQUESTED, "walletlib_2.0.7_start")
            candidate.start()
            AssociationOpenResult.Accepted(parsed.port)
        } catch (_: Throwable) {
            synchronized(lock) {
                if (scenario === candidate) {
                    scenario = null
                }
            }
            runCatching { candidate.close() }
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "scenario_start_failed")
            AssociationOpenResult.Rejected(
                AssociationOpenResult.Reason.SCENARIO_START_FAILED,
            )
        }
    }

    override fun close() {
        closeCurrentScenario(recordClose = true)
        authorizationScope.cancel()
    }

    private fun closeCurrentScenario(recordClose: Boolean) {
        val current = synchronized(lock) {
            scenario.also { scenario = null }
        }

        if (current != null) {
            if (recordClose) {
                record(MwaSessionEvent.CLOSE_REQUESTED, "host")
            }
            runCatching { current.close() }
        }
    }

    private val callbacks = object : LocalScenario.Callbacks {
        override fun onScenarioReady() {
            record(MwaSessionEvent.SCENARIO_READY)
        }

        override fun onScenarioServingClients() {
            record(MwaSessionEvent.SERVING_CLIENTS)
        }

        override fun onScenarioServingComplete() {
            record(MwaSessionEvent.SERVING_COMPLETE)
            onSessionFinished()
        }

        override fun onScenarioComplete() {
            record(MwaSessionEvent.SCENARIO_COMPLETE)
        }

        override fun onScenarioError() {
            record(MwaSessionEvent.SCENARIO_ERROR)
            onSessionFinished()
        }

        override fun onScenarioTeardownComplete() {
            record(MwaSessionEvent.TEARDOWN_COMPLETE)
        }

        override fun onLowPowerAndNoConnection() {
            record(MwaSessionEvent.LOW_POWER_NO_CONNECTION)
            onSessionFinished()
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            record(MwaSessionEvent.AUTHORIZE_REQUEST)

            authorizationScope.launch {
                val decision = try {
                    authorizationPolicy.evaluate(
                        chain = request.chain,
                        requestedFeatures = request.features,
                        requestedAddresses = request.addresses,
                        hasSignInPayload = request.signInPayload != null,
                    )
                } catch (_: Throwable) {
                    record(MwaSessionEvent.AUTHORIZE_IDENTITY_UNAVAILABLE)
                    request.completeWithDecline()
                    return@launch
                }

                when (decision) {
                    is LabAuthorizationDecision.Granted -> {
                        request.completeWithAuthorize(
                            arrayOf(decision.account),
                            null,
                            decision.authorizationScope,
                            null,
                        )
                        record(MwaSessionEvent.AUTHORIZE_SUCCEEDED)
                    }

                    LabAuthorizationDecision.UnsupportedChain -> {
                        record(MwaSessionEvent.AUTHORIZE_CHAIN_REJECTED)
                        request.completeWithClusterNotSupported()
                    }

                    LabAuthorizationDecision.UnsupportedOptionalFeatures -> {
                        record(MwaSessionEvent.AUTHORIZE_OPTIONAL_FEATURES_REJECTED)
                        request.completeWithDecline()
                    }

                    LabAuthorizationDecision.UnsupportedSignIn -> {
                        record(MwaSessionEvent.AUTHORIZE_SIGN_IN_REJECTED)
                        request.completeWithDecline()
                    }

                    LabAuthorizationDecision.RequestedAddressUnavailable -> {
                        record(MwaSessionEvent.AUTHORIZE_REQUESTED_ADDRESS_REJECTED)
                        request.completeWithDecline()
                    }
                }
            }
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            // Phase 2 owns full existing-auth-token / reauthorization UX.
            // Declining here is fail-closed; walletlib revokes the record on decline.
            record(MwaSessionEvent.REAUTHORIZE_DECLINED_PHASE_2_DEFERRED)
            request.completeWithDecline()
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            record(MwaSessionEvent.SIGN_TRANSACTIONS_DECLINED_PHASE_1)
            request.completeWithDecline()
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            record(MwaSessionEvent.SIGN_MESSAGES_DECLINED_PHASE_1)
            request.completeWithDecline()
        }

        override fun onSignAndSendTransactionsRequest(
            request: SignAndSendTransactionsRequest,
        ) {
            record(MwaSessionEvent.SIGN_AND_SEND_DECLINED_PHASE_1)
            request.completeWithDecline()
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            // walletlib has already resolved and revoked the token before this callback.
            event.complete()
            record(MwaSessionEvent.DEAUTHORIZED_COMPLETED)
        }
    }

    private fun record(event: MwaSessionEvent, detail: String? = null) {
        evidenceSink.record(
            MwaSessionEvidence(
                event = event,
                detail = detail,
            ),
        )
    }

    companion object {
        private const val REQUIRED_SCHEME = "solana-wallet"
        private const val AUTH_ISSUER_NAME = "mwa-lab-phase1"
        private const val LOW_POWER_NO_CONNECTION_TIMEOUT_MS = 10_000L
    }
}
