package dev.mwalab.mwa

import android.content.Context
import android.net.Uri
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
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
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidence
import dev.mwalab.mwa.evidence.MwaSessionEvidenceSink
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolEvidenceSink
import dev.mwalab.protocol.ProtocolEvidenceStore
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.security.DiagnosticSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MwaSessionHost(
    context: Context,
    identityRepository: IdentityRepository =
        MwaLabComposition.identityRepository(context.applicationContext),
    private val evidenceSink: MwaSessionEvidenceSink = MwaSessionEvidenceStore,
    private val protocolEvidenceSink: ProtocolEvidenceSink = ProtocolEvidenceStore,
    private val onSessionFinished: () -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val authorizationPolicy = LabAuthorizationPolicy(identityRepository)
    private val authorizationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scenario: Scenario? = null

    private val walletConfig = MwaCapabilityProfile.createWalletConfig()
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
            val startedAt = System.currentTimeMillis()
            val requestSummary = mapOf(
                "chain" to (request.chain ?: "<missing>"),
                "requested_feature_count" to (request.features?.size ?: 0).toString(),
                "requested_address_count" to (request.addresses?.size ?: 0).toString(),
                "sign_in_requested" to (request.signInPayload != null).toString(),
            )

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
                    recordProtocol(
                        method = ProtocolMethod.AUTHORIZE,
                        startedAt = startedAt,
                        outcome = ProtocolOutcome.FAILURE,
                        protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                        failureSource = ProtocolFailureSource.UNKNOWN,
                        requestSummary = requestSummary,
                        responseSummary = mapOf("result" to "identity_unavailable"),
                    )
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
                        recordProtocol(
                            method = ProtocolMethod.AUTHORIZE,
                            startedAt = startedAt,
                            outcome = ProtocolOutcome.SUCCESS,
                            requestSummary = requestSummary,
                            responseSummary = mapOf(
                                "result" to "authorized",
                                "chain" to (request.chain ?: "<missing>"),
                                "public_account" to
                                    (decision.account.displayAddress ?: "<public-key-only>"),
                                "authorization_state" to "walletlib_managed",
                            ),
                        )
                    }

                    LabAuthorizationDecision.UnsupportedChain -> {
                        record(MwaSessionEvent.AUTHORIZE_CHAIN_REJECTED)
                        request.completeWithClusterNotSupported()
                        recordProtocol(
                            method = ProtocolMethod.AUTHORIZE,
                            startedAt = startedAt,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            requestSummary = requestSummary,
                            responseSummary = mapOf("result" to "unsupported_chain"),
                        )
                    }

                    LabAuthorizationDecision.UnsupportedOptionalFeatures -> {
                        record(MwaSessionEvent.AUTHORIZE_OPTIONAL_FEATURES_REJECTED)
                        request.completeWithDecline()
                        recordProtocol(
                            method = ProtocolMethod.AUTHORIZE,
                            startedAt = startedAt,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            requestSummary = requestSummary,
                            responseSummary = mapOf("result" to "unsupported_optional_features"),
                        )
                    }

                    LabAuthorizationDecision.UnsupportedSignIn -> {
                        record(MwaSessionEvent.AUTHORIZE_SIGN_IN_REJECTED)
                        request.completeWithDecline()
                        recordProtocol(
                            method = ProtocolMethod.AUTHORIZE,
                            startedAt = startedAt,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            requestSummary = requestSummary,
                            responseSummary = mapOf("result" to "unsupported_sign_in"),
                        )
                    }

                    LabAuthorizationDecision.RequestedAddressUnavailable -> {
                        record(MwaSessionEvent.AUTHORIZE_REQUESTED_ADDRESS_REJECTED)
                        request.completeWithDecline()
                        recordProtocol(
                            method = ProtocolMethod.AUTHORIZE,
                            startedAt = startedAt,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            requestSummary = requestSummary,
                            responseSummary = mapOf("result" to "requested_address_unavailable"),
                        )
                    }
                }
            }
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
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
            val startedAt = System.currentTimeMillis()
            event.complete()
            record(MwaSessionEvent.DEAUTHORIZED_COMPLETED)
            recordProtocol(
                method = ProtocolMethod.DEAUTHORIZE,
                startedAt = startedAt,
                outcome = ProtocolOutcome.SUCCESS,
                requestSummary = mapOf(
                    "authorization_reference" to "walletlib_managed",
                ),
                responseSummary = mapOf(
                    "result" to "revoked",
                ),
            )
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

    private fun recordProtocol(
        method: ProtocolMethod,
        startedAt: Long,
        outcome: ProtocolOutcome,
        protocolErrorCode: Int? = null,
        failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
        requestSummary: Map<String, String> = emptyMap(),
        responseSummary: Map<String, String> = emptyMap(),
    ) {
        protocolEvidenceSink.record(
            ProtocolEvidence(
                method = method,
                startedAtEpochMillis = startedAt,
                completedAtEpochMillis = System.currentTimeMillis(),
                outcome = outcome,
                protocolErrorCode = protocolErrorCode,
                failureSource = failureSource,
                requestSummary = DiagnosticSanitizer.sanitizeFields(requestSummary),
                responseSummary = DiagnosticSanitizer.sanitizeFields(responseSummary),
            ),
        )
    }

    companion object {
        private const val REQUIRED_SCHEME = "solana-wallet"
        private const val AUTH_ISSUER_NAME = "mwa-lab-phase1"
    }
}
