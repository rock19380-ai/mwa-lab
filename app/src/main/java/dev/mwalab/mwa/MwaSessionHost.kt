package dev.mwalab.mwa

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import dev.mwalab.approval.ApprovalCoordinator
import dev.mwalab.approval.ApprovalDecision
import dev.mwalab.approval.ApprovalRequest
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
import dev.mwalab.security.NetworkDecision
import dev.mwalab.security.NetworkPolicy
import dev.mwalab.signing.LabSigningService
import dev.mwalab.transaction.SolanaTransactionMessageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class MwaSessionHost(
    context: Context,
    identityRepository: IdentityRepository =
        MwaLabComposition.identityRepository(context.applicationContext),
    private val signingService: LabSigningService =
        MwaLabComposition.signingService(context.applicationContext),
    private val approvalCoordinator: ApprovalCoordinator =
        MwaLabComposition.approvalCoordinator(),
    private val evidenceSink: MwaSessionEvidenceSink = MwaSessionEvidenceStore,
    private val protocolEvidenceSink: ProtocolEvidenceSink = ProtocolEvidenceStore,
    private val onSessionFinished: () -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val authorizationPolicy = LabAuthorizationPolicy(identityRepository)
    private val authorizationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scenario: Scenario? = null
    // Local associations can overlap briefly in a singleTask Activity. A late
    // callback from the previous association must never mutate or finish the replacement.
    private val sessionGeneration = AtomicLong(0)
    @Volatile
    private var activeSessionId: String = UUID.randomUUID().toString()
    private val protocolSequence = AtomicLong(0)

    private val walletConfig = MwaCapabilityProfile.createWalletConfig()
    private val authIssuerConfig = AuthIssuerConfig(AUTH_ISSUER_NAME)

    fun openAssociation(uri: Uri?): AssociationOpenResult {
        val generation = sessionGeneration.incrementAndGet()
        closeCurrentScenario(recordClose = false)
        activeSessionId = UUID.randomUUID().toString()
        protocolSequence.set(0)

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
                createCallbacks(generation),
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
        sessionGeneration.incrementAndGet()
        closeCurrentScenario(recordClose = true)
        authorizationScope.cancel()
    }

    private fun closeCurrentScenario(recordClose: Boolean) {
        approvalCoordinator.cancelPending()
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

    private fun createCallbacks(generation: Long) = object : LocalScenario.Callbacks {
        override fun onScenarioReady() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.SCENARIO_READY)
        }

        override fun onScenarioServingClients() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.SERVING_CLIENTS)
        }

        override fun onScenarioServingComplete() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.SERVING_COMPLETE)
            closeCurrentScenario(recordClose = false)
        }

        override fun onScenarioComplete() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.SCENARIO_COMPLETE)
        }

        override fun onScenarioError() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.SCENARIO_ERROR)
            notifySessionFinishedIfCurrent(generation)
        }

        override fun onScenarioTeardownComplete() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.TEARDOWN_COMPLETE)
            notifySessionFinishedIfCurrent(generation)
        }

        override fun onLowPowerAndNoConnection() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.LOW_POWER_NO_CONNECTION)
            notifySessionFinishedIfCurrent(generation)
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            if (!isCurrentGeneration(generation)) {
                request.completeWithDecline()
                return
            }
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
                    if (!isCurrentGeneration(generation)) {
                        request.completeWithDecline()
                        return@launch
                    }
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

                if (!isCurrentGeneration(generation)) {
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
            if (!isCurrentGeneration(generation)) {
                request.completeWithDecline()
                return
            }
            val startedAt = System.currentTimeMillis()
            record(MwaSessionEvent.REAUTHORIZE_REQUEST)
            val requestSummary = mapOf(
                "chain" to request.chain,
                "authorization_reference" to "walletlib_managed",
            )
            val allowed = NetworkPolicy.evaluate(request.chain) is NetworkDecision.Allowed &&
                LabAuthorizationPolicy.isCurrentAuthorizationScope(request.authorizationScope)
            if (allowed) {
                request.completeWithReauthorize()
                record(MwaSessionEvent.REAUTHORIZE_SUCCEEDED)
                recordProtocol(
                    method = ProtocolMethod.REAUTHORIZE,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.SUCCESS,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "reauthorized"),
                )
            } else {
                request.completeWithDecline()
                record(MwaSessionEvent.REAUTHORIZE_REJECTED)
                recordProtocol(
                    method = ProtocolMethod.REAUTHORIZE,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "authorization_context_rejected"),
                )
            }
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            if (!isCurrentGeneration(generation)) {
                request.completeWithDecline()
                return
            }
            // Transaction signing is Phase 2.6+. Keep this callback fail-closed
            // until the bounded legacy transaction codec is in place.
            record(MwaSessionEvent.SIGN_TRANSACTIONS_DECLINED_PHASE_1)
            request.completeWithDecline()
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            if (!isCurrentGeneration(generation)) {
                request.completeWithDecline()
                return
            }
            val startedAt = System.currentTimeMillis()
            val sessionId = activeSessionId
            record(MwaSessionEvent.SIGN_MESSAGES_REQUEST)
            authorizationScope.launch {
                handleSignMessages(
                    request = request,
                    startedAt = startedAt,
                    generation = generation,
                    sessionId = sessionId,
                )
            }
        }

        override fun onSignAndSendTransactionsRequest(
            request: SignAndSendTransactionsRequest,
        ) {
            if (!isCurrentGeneration(generation)) {
                request.completeWithDecline()
                return
            }
            record(MwaSessionEvent.SIGN_AND_SEND_DECLINED_PHASE_1)
            request.completeWithDecline()
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            if (!isCurrentGeneration(generation)) {
                event.complete()
                return
            }
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

    private suspend fun handleSignMessages(
        request: SignMessagesRequest,
        startedAt: Long,
        generation: Long,
        sessionId: String,
    ) {
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            return
        }

        val payloads = request.payloads
        val addresses = request.addresses
        val requestSummary = signingRequestSummary(
            method = ProtocolMethod.SIGN_MESSAGES,
            payloads = payloads,
            addressCount = addresses.size,
            chain = request.chain,
        )

        if (NetworkPolicy.evaluate(request.chain) !is NetworkDecision.Allowed ||
            !LabAuthorizationPolicy.isCurrentAuthorizationScope(request.authorizationScope)
        ) {
            request.completeWithAuthorizationNotValid()
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "authorization_context_rejected"),
            )
            return
        }

        if (payloads.isEmpty() || addresses.isEmpty()) {
            val valid = BooleanArray(payloads.size) { false }
            request.completeWithInvalidPayloads(valid)
            record(MwaSessionEvent.SIGN_MESSAGES_INVALID)
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_INVALID_PAYLOADS,
                failureSource = ProtocolFailureSource.LOCAL_PARSER,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "empty_request"),
            )
            return
        }

        if (payloads.size > MwaCapabilityProfile.MAX_MESSAGES_PER_SIGNING_REQUEST) {
            request.completeWithTooManyPayloads()
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_TOO_MANY_PAYLOADS,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "too_many_payloads"),
            )
            return
        }

        val identity = try {
            signingService.publicIdentity()
        } catch (_: Throwable) {
            request.completeWithInternalError(IllegalStateException("Lab signing identity unavailable"))
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                failureSource = ProtocolFailureSource.UNKNOWN,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "identity_unavailable"),
            )
            return
        }
        val labPublicKey = identity.publicKeyBytes()
        val authorizedPublicKeys = request.authorizedAccounts.map { it.publicKey }
        val allAddressesAuthorized = addresses.all { requested ->
            requested.contentEquals(labPublicKey) &&
                authorizedPublicKeys.any { it.contentEquals(requested) }
        }
        if (!allAddressesAuthorized) {
            request.completeWithAuthorizationNotValid()
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "requested_address_not_authorized"),
            )
            return
        }

        val valid = BooleanArray(payloads.size) { index ->
            val payload = payloads[index]
            payload.isNotEmpty() &&
                payload.size <= MAX_MESSAGE_BYTES &&
                !SolanaTransactionMessageDetector.isTransactionMessage(payload)
        }
        if (!valid.all { it }) {
            request.completeWithInvalidPayloads(valid)
            record(MwaSessionEvent.SIGN_MESSAGES_INVALID)
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_INVALID_PAYLOADS,
                failureSource = ProtocolFailureSource.LOCAL_PARSER,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "invalid_or_transaction_message_payload"),
            )
            return
        }

        val approval = approvalCoordinator.requestApproval(
            ApprovalRequest(
                sessionId = sessionId,
                method = ProtocolMethod.SIGN_MESSAGES.wireName,
                dappIdentityName = request.identityName,
                chain = request.chain,
                payloadFingerprints = payloads.map { DiagnosticSanitizer.sha256(it) },
                payloadLengths = payloads.map { it.size },
            ),
        )
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            return
        }

        if (approval !is ApprovalDecision.Approved) {
            request.completeWithDecline()
            record(MwaSessionEvent.SIGN_MESSAGES_REJECTED)
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = if (approval is ApprovalDecision.Cancelled) {
                    ProtocolOutcome.CANCELLED
                } else {
                    ProtocolOutcome.FAILURE
                },
                protocolErrorCode = ProtocolContract.ERROR_NOT_SIGNED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to approval.javaClass.simpleName.lowercase()),
            )
            return
        }

        record(MwaSessionEvent.SIGN_MESSAGES_APPROVED)
        try {
            val signedPayloads = ArrayList<ByteArray>(payloads.size)
            for (payload in payloads) {
                val signatures = ArrayList<ByteArray>(addresses.size)
                for (ignoredAddress in addresses) {
                    @Suppress("UNUSED_VARIABLE")
                    val ignored = ignoredAddress
                    signatures += signingService.sign(payload)
                }
                val totalSize = payload.size + signatures.sumOf { it.size }
                signedPayloads += ByteArray(totalSize).also { out ->
                    payload.copyInto(out, destinationOffset = 0)
                    var offset = payload.size
                    signatures.forEach { signature ->
                        signature.copyInto(out, destinationOffset = offset)
                        offset += signature.size
                    }
                }
            }
            val signedPayloadArray = signedPayloads.toTypedArray()
            request.completeWithSignedPayloads(signedPayloadArray)
            record(MwaSessionEvent.SIGN_MESSAGES_SUCCEEDED)
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.SUCCESS,
                requestSummary = requestSummary,
                responseSummary = mapOf(
                    "result" to "signed",
                    "signed_payload_count" to signedPayloadArray.size.toString(),
                ),
            )
        } catch (_: Throwable) {
            request.completeWithInternalError(IllegalStateException("Lab signing failed"))
            recordProtocol(
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                failureSource = ProtocolFailureSource.UNKNOWN,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "signing_failed"),
            )
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        sessionGeneration.get() == generation

    private fun notifySessionFinishedIfCurrent(generation: Long) {
        // Re-check on the main queue itself; checking only on the walletlib callback
        // thread still leaves a race with a newer onNewIntent association.
        mainHandler.post {
            if (isCurrentGeneration(generation)) {
                onSessionFinished()
            }
        }
    }

    private fun signingRequestSummary(
        method: ProtocolMethod,
        payloads: Array<ByteArray>,
        addressCount: Int,
        chain: String,
    ): Map<String, String> = buildMap {
        put("method", method.wireName)
        put("chain", chain)
        put("payload_count", payloads.size.toString())
        put("address_count", addressCount.toString())
        payloads.forEachIndexed { index, payload ->
            put("payload_${index}_sha256", DiagnosticSanitizer.sha256(payload))
            put("payload_${index}_length", payload.size.toString())
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
        val sequence = protocolSequence.incrementAndGet()
        val sessionId = activeSessionId
        protocolEvidenceSink.record(
            ProtocolEvidence(
                sessionId = sessionId,
                eventId = "$sessionId:$sequence",
                sequence = sequence,
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
        private const val MAX_MESSAGE_BYTES = 64 * 1024
    }
}
