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
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.capabilities.snapshotForSession
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
import dev.mwalab.protocol.recorder.ProtocolEventHandle
import dev.mwalab.protocol.recorder.ProtocolRecorder
import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSendOptions
import dev.mwalab.rpc.DevnetSimulationOptions
import dev.mwalab.rpc.SignAndSendFatalReason
import dev.mwalab.rpc.SignAndSendSubmission
import dev.mwalab.rpc.SignAndSendSubmissionExecutor
import dev.mwalab.rpc.SignAndSendSubmissionResult
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionLifecycleCoordinator
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.simulation.SimulationDiagnosticSettlement
import dev.mwalab.simulation.SimulationTargetRef
import dev.mwalab.simulation.TransactionSimulationCoordinator
import dev.mwalab.security.NetworkDecision
import dev.mwalab.security.NetworkPolicy
import dev.mwalab.signing.LabSigningService
import dev.mwalab.transaction.PreApprovalTransactionInspection
import dev.mwalab.transaction.TransactionApprovalDiagnostics
import dev.mwalab.transaction.TransactionInspection
import dev.mwalab.transaction.TransactionDiagnosticSettlement
import dev.mwalab.transaction.LegacyTransactionCodec
import dev.mwalab.transaction.SolanaTransactionMessageDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val rpcGateway: DevnetRpcGateway = MwaLabComposition.devnetRpcGateway(),
    private val evidenceSink: MwaSessionEvidenceSink = MwaSessionEvidenceStore,
    private val protocolEvidenceSink: ProtocolEvidenceSink = ProtocolEvidenceStore,
    private val protocolRecorder: ProtocolRecorder =
        MwaLabComposition.protocolRecorder(context.applicationContext),
    private val sessionLifecycleCoordinator: SessionLifecycleCoordinator =
        MwaLabComposition.sessionLifecycleCoordinator(context.applicationContext),
    private val capabilitySnapshotRepository: CapabilitySnapshotRepository =
        MwaLabComposition.capabilitySnapshotRepository(context.applicationContext),
    transactionInspector: TransactionInspection = MwaLabComposition.transactionInspector(),
    private val transactionDiagnosticSettlement: TransactionDiagnosticSettlement =
        MwaLabComposition.transactionDiagnosticSettlement(context.applicationContext),
    private val simulationCoordinator: TransactionSimulationCoordinator =
        MwaLabComposition.simulationCoordinator(context.applicationContext),
    private val simulationDiagnosticSettlement: SimulationDiagnosticSettlement =
        MwaLabComposition.simulationDiagnosticSettlement(context.applicationContext),
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
    private val activeAuthorizationGeneration = AtomicLong(NO_AUTHORIZATION_GENERATION)
    @Volatile
    private var activeSessionId: String = UUID.randomUUID().toString()
    @Volatile
    private var activePersistentSessionId: String? = null
    private val protocolSequence = AtomicLong(0)

    private val transactionInspection = PreApprovalTransactionInspection(
        transactionInspector, MwaCapabilityProfile.MAX_TRANSACTIONS_PER_SIGNING_REQUEST,
    )

    private val walletConfig = MwaCapabilityProfile.createWalletConfig()
    private val authIssuerConfig = AuthIssuerConfig(AUTH_ISSUER_NAME)

    fun openAssociation(uri: Uri?): AssociationOpenResult {
        simulationCoordinator.invalidateSession(activeSessionId, sessionGeneration.get())
        simulationDiagnosticSettlement.invalidateSession(activeSessionId)
        val generation = sessionGeneration.incrementAndGet()
        activeAuthorizationGeneration.set(NO_AUTHORIZATION_GENERATION)
        val replacedSessionId = activePersistentSessionId
        closeCurrentScenario(recordClose = false)
        if (replacedSessionId != null) {
            finishPersistentSession(
                sessionId = replacedSessionId,
                closeReason = SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT,
            )
            clearActivePersistentSessionIfMatches(replacedSessionId)
        }
        activeSessionId = UUID.randomUUID().toString()
        simulationCoordinator.activateSession(activeSessionId, generation)
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
                createCallbacks(generation, activeSessionId),
            )
        } catch (_: Throwable) {
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "scenario_creation_failed")
            return AssociationOpenResult.Rejected(
                AssociationOpenResult.Reason.SCENARIO_CREATION_FAILED,
            )
        }

        val persistentSessionId = activeSessionId
        if (createPersistentSession(persistentSessionId) is
            SessionLifecycleCoordinator.PersistenceResult.Persisted
        ) {
            captureSessionCapabilities(persistentSessionId)
        }
        activePersistentSessionId = persistentSessionId

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
            finishPersistentSession(
                sessionId = persistentSessionId,
                closeReason = SessionCloseReason.START_FAILED,
            )
            runCatching { candidate.close() }
            clearActivePersistentSessionIfMatches(persistentSessionId)
            record(MwaSessionEvent.ASSOCIATION_REJECTED, "scenario_start_failed")
            AssociationOpenResult.Rejected(
                AssociationOpenResult.Reason.SCENARIO_START_FAILED,
            )
        }
    }

    override fun close() {
        simulationCoordinator.invalidateSession(activeSessionId, sessionGeneration.get())
        simulationDiagnosticSettlement.invalidateSession(activeSessionId)
        sessionGeneration.incrementAndGet()
        activeAuthorizationGeneration.set(NO_AUTHORIZATION_GENERATION)
        val sessionId = activePersistentSessionId
        closeCurrentScenario(recordClose = true)
        if (sessionId != null) {
            finishPersistentSession(sessionId, SessionCloseReason.HOST_CLOSED)
            clearActivePersistentSessionIfMatches(sessionId)
        }
        authorizationScope.cancel()
    }

    private fun closeCurrentScenario(recordClose: Boolean) {
        simulationCoordinator.invalidateSession(activeSessionId, sessionGeneration.get())
        simulationDiagnosticSettlement.invalidateSession(activeSessionId)
        activeAuthorizationGeneration.set(NO_AUTHORIZATION_GENERATION)
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

    private fun createCallbacks(
        generation: Long,
        persistentSessionId: String,
    ) = object : LocalScenario.Callbacks {
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
            finishPersistentSession(persistentSessionId, SessionCloseReason.SERVING_COMPLETE)
            clearActivePersistentSessionIfMatches(persistentSessionId)
            closeCurrentScenario(recordClose = false)
        }

        override fun onScenarioComplete() {
            if (!isCurrentGeneration(generation)) return
            simulationCoordinator.invalidateSession(persistentSessionId, generation)
            simulationDiagnosticSettlement.invalidateSession(persistentSessionId)
            record(MwaSessionEvent.SCENARIO_COMPLETE)
            finishPersistentSession(persistentSessionId, SessionCloseReason.SCENARIO_COMPLETE)
            clearActivePersistentSessionIfMatches(persistentSessionId)
        }

        override fun onScenarioError() {
            if (!isCurrentGeneration(generation)) return
            simulationCoordinator.invalidateSession(persistentSessionId, generation)
            simulationDiagnosticSettlement.invalidateSession(persistentSessionId)
            record(MwaSessionEvent.SCENARIO_ERROR)
            finishPersistentSession(persistentSessionId, SessionCloseReason.SCENARIO_ERROR)
            clearActivePersistentSessionIfMatches(persistentSessionId)
            notifySessionFinishedIfCurrent(generation)
        }

        override fun onScenarioTeardownComplete() {
            if (!isCurrentGeneration(generation)) return
            simulationCoordinator.invalidateSession(persistentSessionId, generation)
            simulationDiagnosticSettlement.invalidateSession(persistentSessionId)
            record(MwaSessionEvent.TEARDOWN_COMPLETE)
            finishPersistentSession(persistentSessionId, SessionCloseReason.TEARDOWN_COMPLETE)
            clearActivePersistentSessionIfMatches(persistentSessionId)
            notifySessionFinishedIfCurrent(generation)
        }

        override fun onLowPowerAndNoConnection() {
            if (!isCurrentGeneration(generation)) return
            record(MwaSessionEvent.LOW_POWER_NO_CONNECTION)
            finishPersistentSession(
                persistentSessionId,
                SessionCloseReason.LOW_POWER_NO_CONNECTION,
            )
            clearActivePersistentSessionIfMatches(persistentSessionId)
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
            val protocolHandle = beginPersistentProtocol(
                sessionId = persistentSessionId,
                method = ProtocolMethod.AUTHORIZE,
                requestSummary = requestSummary,
            )
            updatePersistentDappIdentity(persistentSessionId, request.identityName)

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
                    completePersistentProtocol(
                        handle = protocolHandle,
                        outcome = ProtocolOutcome.FAILURE,
                        protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                        failureSource = ProtocolFailureSource.UNKNOWN,
                        responseSummary = mapOf("result" to "identity_unavailable"),
                    )
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
                        markAuthorizationActive(generation)
                        request.completeWithAuthorize(
                            arrayOf(decision.account),
                            null,
                            decision.authorizationScope,
                            null,
                        )
                        record(MwaSessionEvent.AUTHORIZE_SUCCEEDED)
                        completePersistentProtocol(
                            handle = protocolHandle,
                            outcome = ProtocolOutcome.SUCCESS,
                            responseSummary = mapOf(
                                "result" to "authorized",
                                "chain" to (request.chain ?: "<missing>"),
                                "public_account" to
                                    (decision.account.displayAddress ?: "<public-key-only>"),
                                "authorization_state" to "walletlib_managed",
                            ),
                        )
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
                        completePersistentProtocol(
                            handle = protocolHandle,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            responseSummary = mapOf("result" to "unsupported_chain"),
                        )
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
                        completePersistentProtocol(
                            handle = protocolHandle,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            responseSummary = mapOf("result" to "unsupported_optional_features"),
                        )
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
                        completePersistentProtocol(
                            handle = protocolHandle,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            responseSummary = mapOf("result" to "unsupported_sign_in"),
                        )
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
                        completePersistentProtocol(
                            handle = protocolHandle,
                            outcome = ProtocolOutcome.FAILURE,
                            protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                            responseSummary = mapOf("result" to "requested_address_unavailable"),
                        )
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
            val requestSummary = mapOf(
                "chain" to request.chain,
                "authorization_reference" to "walletlib_managed",
            )
            val protocolHandle = beginPersistentProtocol(
                sessionId = persistentSessionId,
                method = ProtocolMethod.REAUTHORIZE,
                requestSummary = requestSummary,
            )
            updatePersistentDappIdentity(persistentSessionId, request.identityName)
            record(MwaSessionEvent.REAUTHORIZE_REQUEST)
            val allowed = NetworkPolicy.evaluate(request.chain) is NetworkDecision.Allowed &&
                LabAuthorizationPolicy.isCurrentAuthorizationScope(request.authorizationScope)
            if (allowed) {
                markAuthorizationActive(generation)
                request.completeWithReauthorize()
                record(MwaSessionEvent.REAUTHORIZE_SUCCEEDED)
                completePersistentProtocolBlocking(
                    handle = protocolHandle,
                    outcome = ProtocolOutcome.SUCCESS,
                    responseSummary = mapOf("result" to "reauthorized"),
                )
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
                completePersistentProtocolBlocking(
                    handle = protocolHandle,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                    responseSummary = mapOf("result" to "authorization_context_rejected"),
                )
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
            val startedAt = System.currentTimeMillis()
            val payloads = transactionInspection.ownPayloads(request.payloads)
            val requestSummary = signingRequestSummary(
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                payloads = payloads,
                addressCount = request.authorizedAccounts.size,
                chain = request.chain,
            )
            val protocolHandle = beginPersistentProtocol(
                sessionId = persistentSessionId,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                requestSummary = requestSummary,
            )
            updatePersistentDappIdentity(persistentSessionId, request.identityName)
            record(MwaSessionEvent.SIGN_TRANSACTIONS_REQUEST)
            val approvalRequestId = UUID.randomUUID().toString()
            authorizationScope.launch {
                val simulationContext = simulationDiagnosticSettlement.begin(protocolHandle, approvalRequestId) {
                    record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "simulation_results")
                }
                val diagnostics = transactionInspection.inspect(payloads, persistentSessionId, protocolHandle?.eventId)
                val diagnosticContext = transactionDiagnosticSettlement.begin(protocolHandle, diagnostics) {
                    record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "transaction_diagnostics")
                }
                try {
                    handleSignTransactions(
                        request = request,
                        payloads = payloads,
                        diagnostics = diagnostics,
                        startedAt = startedAt,
                        generation = generation,
                        sessionId = persistentSessionId,
                        protocolHandle = protocolHandle,
                        requestSummary = requestSummary,
                        approvalRequestId = approvalRequestId,
                        simulationEnabled = simulationContext != null,
                    )
                } finally {
                    // Independent diagnostic work survives cancellation of this session's authorization scope.
                    protocolHandle?.let { simulationCoordinator.releaseRequest(persistentSessionId, it.eventId, approvalRequestId) }
                    simulationDiagnosticSettlement.finish(simulationContext)
                    transactionDiagnosticSettlement.finish(diagnosticContext)
                }
            }
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            if (!isCurrentGeneration(generation)) {
                request.completeWithDecline()
                return
            }
            val startedAt = System.currentTimeMillis()
            val requestSummary = signingRequestSummary(
                method = ProtocolMethod.SIGN_MESSAGES,
                payloads = request.payloads,
                addressCount = request.addresses.size,
                chain = request.chain,
            )
            val protocolHandle = beginPersistentProtocol(
                sessionId = persistentSessionId,
                method = ProtocolMethod.SIGN_MESSAGES,
                requestSummary = requestSummary,
            )
            updatePersistentDappIdentity(persistentSessionId, request.identityName)
            record(MwaSessionEvent.SIGN_MESSAGES_REQUEST)
            authorizationScope.launch {
                handleSignMessages(
                    request = request,
                    startedAt = startedAt,
                    generation = generation,
                    sessionId = persistentSessionId,
                    protocolHandle = protocolHandle,
                    requestSummary = requestSummary,
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
            val startedAt = System.currentTimeMillis()
            val payloads = transactionInspection.ownPayloads(request.payloads)
            val requestSummary = signingRequestSummary(
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                payloads = payloads,
                addressCount = request.authorizedAccounts.size,
                chain = request.chain,
            ) + mapOf(
                "min_context_slot_present" to (request.minContextSlot != null).toString(),
                "commitment" to (request.commitment ?: "<default>"),
                "skip_preflight" to (request.skipPreflight?.toString() ?: "<default>"),
                "max_retries_present" to (request.maxRetries != null).toString(),
                "wait_for_commitment" to
                    (request.waitForCommitmentToSendNextTransaction?.toString() ?: "<default>"),
            )
            val protocolHandle = beginPersistentProtocol(
                sessionId = persistentSessionId,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                requestSummary = requestSummary,
            )
            updatePersistentDappIdentity(persistentSessionId, request.identityName)
            record(MwaSessionEvent.SIGN_AND_SEND_REQUEST)
            val approvalRequestId = UUID.randomUUID().toString()
            authorizationScope.launch {
                val simulationContext = simulationDiagnosticSettlement.begin(protocolHandle, approvalRequestId) {
                    record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "simulation_results")
                }
                val diagnostics = transactionInspection.inspect(payloads, persistentSessionId, protocolHandle?.eventId)
                val diagnosticContext = transactionDiagnosticSettlement.begin(protocolHandle, diagnostics) {
                    record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "transaction_diagnostics")
                }
                try {
                    handleSignAndSendTransactions(
                        request = request,
                        payloads = payloads,
                        diagnostics = diagnostics,
                        startedAt = startedAt,
                        generation = generation,
                        sessionId = persistentSessionId,
                        protocolHandle = protocolHandle,
                        requestSummary = requestSummary,
                        approvalRequestId = approvalRequestId,
                        simulationEnabled = simulationContext != null,
                    )
                } finally {
                    // Independent diagnostic work survives cancellation of this session's authorization scope.
                    protocolHandle?.let { simulationCoordinator.releaseRequest(persistentSessionId, it.eventId, approvalRequestId) }
                    simulationDiagnosticSettlement.finish(simulationContext)
                    transactionDiagnosticSettlement.finish(diagnosticContext)
                }
            }
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            if (!isCurrentGeneration(generation)) {
                event.complete()
                return
            }
            val startedAt = System.currentTimeMillis()
            val protocolHandle = beginPersistentProtocol(
                sessionId = persistentSessionId,
                method = ProtocolMethod.DEAUTHORIZE,
                requestSummary = mapOf(
                    "authorization_reference" to "walletlib_managed",
                ),
            )
            updatePersistentDappIdentity(persistentSessionId, event.identityName)
            invalidateAuthorization(generation)
            event.complete()
            record(MwaSessionEvent.DEAUTHORIZED_COMPLETED)
            completePersistentProtocolBlocking(
                handle = protocolHandle,
                outcome = ProtocolOutcome.SUCCESS,
                responseSummary = mapOf("result" to "revoked"),
            )
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

    private sealed interface TransactionPreparationResult {
        data class Ready(
            val transactions: List<LegacyTransactionCodec.Parsed>,
        ) : TransactionPreparationResult

        data class Invalid(
            val valid: BooleanArray,
            val reason: String,
            val failureSource: ProtocolFailureSource,
        ) : TransactionPreparationResult

        data object AuthorizationInvalid : TransactionPreparationResult
        data object IdentityUnavailable : TransactionPreparationResult
        data object RpcUnavailable : TransactionPreparationResult
    }

    private data class SignedLegacyTransaction(
        val parsed: LegacyTransactionCodec.Parsed,
        val payload: ByteArray,
    )

    private suspend fun prepareLegacyTransactions(
        payloads: Array<ByteArray>,
        authorizedPublicKeys: List<ByteArray>,
        chain: String,
        authorizationScopeBytes: ByteArray,
        minContextSlot: Int? = null,
    ): TransactionPreparationResult {
        if (NetworkPolicy.evaluate(chain) !is NetworkDecision.Allowed ||
            !LabAuthorizationPolicy.isCurrentAuthorizationScope(authorizationScopeBytes)
        ) {
            return TransactionPreparationResult.AuthorizationInvalid
        }

        if (payloads.isEmpty()) {
            return TransactionPreparationResult.Invalid(
                valid = BooleanArray(0),
                reason = "empty_request",
                failureSource = ProtocolFailureSource.LOCAL_PARSER,
            )
        }

        val identity = try {
            signingService.publicIdentity()
        } catch (_: Throwable) {
            return TransactionPreparationResult.IdentityUnavailable
        }
        val signerPublicKey = identity.publicKeyBytes()
        if (authorizedPublicKeys.none { it.contentEquals(signerPublicKey) }) {
            return TransactionPreparationResult.AuthorizationInvalid
        }

        val valid = BooleanArray(payloads.size) { true }
        val parsed = ArrayList<LegacyTransactionCodec.Parsed>(payloads.size)
        payloads.forEachIndexed { index, payload ->
            try {
                parsed += LegacyTransactionCodec.parseForSigner(payload, signerPublicKey)
            } catch (_: LegacyTransactionCodec.Rejected) {
                valid[index] = false
            }
        }
        if (!valid.all { it }) {
            return TransactionPreparationResult.Invalid(
                valid = valid,
                reason = "invalid_legacy_transaction",
                failureSource = ProtocolFailureSource.LOCAL_PARSER,
            )
        }

        parsed.forEachIndexed { index, transaction ->
            when (
                val blockhashResult = rpcGateway.isBlockhashValid(
                    blockhash = transaction.recentBlockhash,
                    minContextSlot = minContextSlot,
                )
            ) {
                is DevnetRpcResult.Success -> {
                    if (!blockhashResult.value) valid[index] = false
                }

                is DevnetRpcResult.RpcError,
                is DevnetRpcResult.TransportFailure,
                DevnetRpcResult.MalformedResponse -> return TransactionPreparationResult.RpcUnavailable
            }
        }
        if (!valid.all { it }) {
            return TransactionPreparationResult.Invalid(
                valid = valid,
                reason = "blockhash_not_valid_on_devnet",
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            )
        }

        return TransactionPreparationResult.Ready(parsed)
    }

    private suspend fun signLegacyTransactions(
        prepared: List<LegacyTransactionCodec.Parsed>,
    ): List<SignedLegacyTransaction> {
        val result = ArrayList<SignedLegacyTransaction>(prepared.size)
        for (transaction in prepared) {
            val signature = signingService.sign(transaction.message)
            require(signature.size == LegacyTransactionCodec.SIGNATURE_BYTES) {
                "Unexpected Ed25519 signature length"
            }
            result += SignedLegacyTransaction(
                parsed = transaction,
                payload = transaction.withSignature(signature),
            )
        }
        return result
    }

    private fun registerSimulationTargets(
        ready: TransactionPreparationResult.Ready,
        sessionId: String,
        handle: ProtocolEventHandle?,
        requestId: String,
        generation: Long,
        enabled: Boolean,
        options: DevnetSimulationOptions,
    ): List<SimulationTargetRef?> {
        if (!enabled || handle == null || !isCurrentGeneration(generation)) {
            return List(ready.transactions.size) { null }
        }
        return ready.transactions.mapIndexed { index, parsed ->
            runCatching {
                val ref = SimulationTargetRef(
                    sessionId, handle.eventId, requestId, index,
                    DiagnosticSanitizer.sha256(parsed.original),
                )
                if (simulationCoordinator.register(ref, generation, parsed.original, options)) ref else null
            }.getOrNull()
        }
    }

    private suspend fun handleSignTransactions(
        request: SignTransactionsRequest,
        payloads: Array<ByteArray>,
        diagnostics: TransactionApprovalDiagnostics,
        startedAt: Long,
        generation: Long,
        sessionId: String,
        protocolHandle: ProtocolEventHandle?,
        requestSummary: Map<String, String>,
        approvalRequestId: String,
        simulationEnabled: Boolean,
    ) {
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_session_generation"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "session_authorization_inactive"),
            )
            return
        }

        if (payloads.size > MwaCapabilityProfile.MAX_TRANSACTIONS_PER_SIGNING_REQUEST) {
            request.completeWithTooManyPayloads()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_TOO_MANY_PAYLOADS,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "too_many_payloads"),
            )
            return
        }

        val preparation = prepareLegacyTransactions(
            payloads = payloads,
            authorizedPublicKeys = request.authorizedAccounts.map { it.publicKey },
            chain = request.chain,
            authorizationScopeBytes = request.authorizationScope,
        )
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_after_preparation"),
            )
            return
        }

        val ready = when (preparation) {
            is TransactionPreparationResult.Ready -> preparation
            is TransactionPreparationResult.Invalid -> {
                request.completeWithInvalidPayloads(preparation.valid)
                record(MwaSessionEvent.SIGN_TRANSACTIONS_INVALID)
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_INVALID_PAYLOADS,
                    failureSource = preparation.failureSource,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to preparation.reason),
                )
                return
            }

            TransactionPreparationResult.AuthorizationInvalid -> {
                request.completeWithAuthorizationNotValid()
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "authorization_context_rejected"),
                )
                return
            }

            TransactionPreparationResult.IdentityUnavailable -> {
                request.completeWithInternalError(IllegalStateException("Lab signing identity unavailable"))
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    failureSource = ProtocolFailureSource.UNKNOWN,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "identity_unavailable"),
                )
                return
            }

            TransactionPreparationResult.RpcUnavailable -> {
                request.completeWithInternalError(IllegalStateException("Devnet blockhash validation unavailable"))
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    failureSource = ProtocolFailureSource.RPC_NETWORK,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "devnet_blockhash_validation_unavailable"),
                )
                return
            }
        }

        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "authorization_revoked_before_approval"),
            )
            return
        }

        val simulationTargets = registerSimulationTargets(
            ready, sessionId, protocolHandle, approvalRequestId, generation, simulationEnabled,
            DevnetSimulationOptions.forSignTransactions(),
        )
        val approval = approvalCoordinator.requestApproval(
            ApprovalRequest(
                requestId = approvalRequestId,
                simulationTargets = simulationTargets,
                sessionId = sessionId,
                method = ProtocolMethod.SIGN_TRANSACTIONS.wireName,
                dappIdentityName = request.identityName,
                chain = request.chain,
                payloadFingerprints = payloads.map { DiagnosticSanitizer.sha256(it) },
                payloadLengths = payloads.map { it.size },
                transactionSummaries = diagnostics,
            ),
        )
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_during_approval"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "authorization_revoked_during_approval"),
            )
            return
        }
        if (approval !is ApprovalDecision.Approved) {
            request.completeWithDecline()
            record(MwaSessionEvent.SIGN_TRANSACTIONS_REJECTED)
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
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

        record(MwaSessionEvent.SIGN_TRANSACTIONS_APPROVED)
        val signed = try {
            signLegacyTransactions(ready.transactions)
        } catch (_: Throwable) {
            request.completeWithInternalError(IllegalStateException("Lab transaction signing failed"))
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                failureSource = ProtocolFailureSource.UNKNOWN,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "signing_failed"),
            )
            return
        }
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_after_signing"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "authorization_revoked_after_signing"),
            )
            return
        }

        request.completeWithSignedPayloads(signed.map { it.payload }.toTypedArray())
        record(MwaSessionEvent.SIGN_TRANSACTIONS_SUCCEEDED)
        recordMigratedSigningProtocol(
            handle = protocolHandle,
            method = ProtocolMethod.SIGN_TRANSACTIONS,
            startedAt = startedAt,
            outcome = ProtocolOutcome.SUCCESS,
            requestSummary = requestSummary,
            responseSummary = mapOf(
                "result" to "signed",
                "signed_payload_count" to signed.size.toString(),
                "transaction_version" to "legacy",
                "network_validation" to "devnet_blockhash_valid",
            ),
        )
    }

    private suspend fun handleSignAndSendTransactions(
        request: SignAndSendTransactionsRequest,
        payloads: Array<ByteArray>,
        diagnostics: TransactionApprovalDiagnostics,
        startedAt: Long,
        generation: Long,
        sessionId: String,
        protocolHandle: ProtocolEventHandle?,
        requestSummary: Map<String, String>,
        approvalRequestId: String,
        simulationEnabled: Boolean,
    ) {
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_session_generation"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "session_authorization_inactive"),
            )
            return
        }

        if (payloads.size > MwaCapabilityProfile.MAX_TRANSACTIONS_PER_SIGNING_REQUEST) {
            request.completeWithTooManyPayloads()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_TOO_MANY_PAYLOADS,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "too_many_payloads"),
            )
            return
        }

        val sendOptions = DevnetSendOptions(
            minContextSlot = request.minContextSlot,
            commitment = request.commitment,
            skipPreflight = request.skipPreflight,
            maxRetries = request.maxRetries,
            waitForCommitmentToSendNextTransaction = request.waitForCommitmentToSendNextTransaction,
        ).validatedOrNull()
        if (sendOptions == null) {
            request.completeWithInternalError(IllegalArgumentException("Unsupported sign_and_send options"))
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                failureSource = ProtocolFailureSource.LOCAL_PARSER,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "invalid_send_options"),
            )
            return
        }

        val preparation = prepareLegacyTransactions(
            payloads = payloads,
            authorizedPublicKeys = request.authorizedAccounts.map { it.publicKey },
            chain = request.chain,
            authorizationScopeBytes = request.authorizationScope,
            minContextSlot = sendOptions.minContextSlot,
        )
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_after_preparation"),
            )
            return
        }

        val ready = when (preparation) {
            is TransactionPreparationResult.Ready -> preparation
            is TransactionPreparationResult.Invalid -> {
                request.completeWithInvalidSignatures(preparation.valid)
                record(MwaSessionEvent.SIGN_AND_SEND_INVALID)
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_INVALID_PAYLOADS,
                    failureSource = preparation.failureSource,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to preparation.reason),
                )
                return
            }

            TransactionPreparationResult.AuthorizationInvalid -> {
                request.completeWithAuthorizationNotValid()
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "authorization_context_rejected"),
                )
                return
            }

            TransactionPreparationResult.IdentityUnavailable -> {
                request.completeWithInternalError(IllegalStateException("Lab signing identity unavailable"))
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    failureSource = ProtocolFailureSource.UNKNOWN,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "identity_unavailable"),
                )
                return
            }

            TransactionPreparationResult.RpcUnavailable -> {
                request.completeWithInternalError(IllegalStateException("Devnet blockhash validation unavailable"))
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    failureSource = ProtocolFailureSource.RPC_NETWORK,
                    requestSummary = requestSummary,
                    responseSummary = mapOf("result" to "devnet_blockhash_validation_unavailable"),
                )
                return
            }
        }

        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "authorization_revoked_before_approval"),
            )
            return
        }

        val simulationTargets = registerSimulationTargets(
            ready, sessionId, protocolHandle, approvalRequestId, generation, simulationEnabled,
            DevnetSimulationOptions.forSignAndSend(sendOptions),
        )
        val approval = approvalCoordinator.requestApproval(
            ApprovalRequest(
                requestId = approvalRequestId,
                simulationTargets = simulationTargets,
                sessionId = sessionId,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS.wireName,
                dappIdentityName = request.identityName,
                chain = request.chain,
                payloadFingerprints = payloads.map { DiagnosticSanitizer.sha256(it) },
                payloadLengths = payloads.map { it.size },
                transactionSummaries = diagnostics,
            ),
        )
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_during_approval"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "authorization_revoked_during_approval"),
            )
            return
        }
        if (approval !is ApprovalDecision.Approved) {
            request.completeWithDecline()
            record(MwaSessionEvent.SIGN_AND_SEND_REJECTED)
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
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

        record(MwaSessionEvent.SIGN_AND_SEND_APPROVED)
        val signed = try {
            signLegacyTransactions(ready.transactions)
        } catch (_: Throwable) {
            request.completeWithInternalError(IllegalStateException("Lab transaction signing failed"))
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                failureSource = ProtocolFailureSource.UNKNOWN,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "signing_failed"),
            )
            return
        }
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_after_signing"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "authorization_revoked_after_signing"),
            )
            return
        }

        val submissionResult = SignAndSendSubmissionExecutor(rpcGateway).execute(
            transactions = signed.map { transaction ->
                SignAndSendSubmission(
                    payload = transaction.payload,
                    expectedSignature = transaction.parsed.primarySignature(transaction.payload),
                )
            },
            options = sendOptions,
            isRequestCurrent = { isAuthorizationActive(generation) },
        )

        when (submissionResult) {
            SignAndSendSubmissionResult.Cancelled -> {
                val authorizationInactive =
                    isCurrentGeneration(generation) && !isAuthorizationActive(generation)
                if (authorizationInactive) {
                    request.completeWithAuthorizationNotValid()
                } else {
                    request.completeWithDecline()
                }
                completePersistentProtocol(
                    handle = protocolHandle,
                    outcome = ProtocolOutcome.CANCELLED,
                    protocolErrorCode = if (authorizationInactive) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED
                    } else {
                        ProtocolContract.ERROR_NOT_SIGNED
                    },
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                    responseSummary = mapOf("result" to "submission_cancelled"),
                )
                return
            }

            is SignAndSendSubmissionResult.NotSubmitted -> {
                request.completeWithNotSubmitted(submissionResult.signatures.toTypedArray())
                record(MwaSessionEvent.SIGN_AND_SEND_NOT_SUBMITTED)
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_NOT_SUBMITTED,
                    failureSource = ProtocolFailureSource.RPC_NETWORK,
                    requestSummary = requestSummary,
                    responseSummary = mapOf(
                        "result" to "one_or_more_not_submitted",
                        "submitted_count" to submissionResult.signatures.count { it != null }.toString(),
                        "not_submitted_count" to submissionResult.signatures.count { it == null }.toString(),
                    ),
                )
                return
            }

            is SignAndSendSubmissionResult.Fatal -> {
                val message = when (submissionResult.reason) {
                    SignAndSendFatalReason.SIGNATURE_MISMATCH ->
                        "Devnet RPC returned an unexpected transaction signature"
                    SignAndSendFatalReason.COMMITMENT_NOT_REACHED_BEFORE_NEXT,
                    SignAndSendFatalReason.COMMITMENT_NOT_REACHED ->
                        "Devnet commitment verification did not succeed"
                    SignAndSendFatalReason.COMMITMENT_UNAVAILABLE_BEFORE_NEXT,
                    SignAndSendFatalReason.COMMITMENT_UNAVAILABLE ->
                        "Devnet commitment verification unavailable"
                }
                request.completeWithInternalError(IllegalStateException(message))
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    failureSource = ProtocolFailureSource.RPC_NETWORK,
                    requestSummary = requestSummary,
                    responseSummary = mapOf(
                        "result" to submissionResult.reason.evidenceResult,
                        "submitted_count" to submissionResult.submittedCount.toString(),
                    ),
                )
                return
            }

            is SignAndSendSubmissionResult.Submitted -> {
                val signatures = submissionResult.signatures.toTypedArray()
                request.completeWithSignatures(signatures)
                record(MwaSessionEvent.SIGN_AND_SEND_SUBMITTED)
                recordMigratedSigningProtocol(
                handle = protocolHandle,
                    method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.SUCCESS,
                    requestSummary = requestSummary,
                    responseSummary = mapOf(
                        "result" to "submitted",
                        "submitted_count" to signatures.size.toString(),
                        "rpc_network" to "solana_devnet",
                        "commitment_verified" to (sendOptions.commitment != null).toString(),
                    ),
                )
            }
        }
    }

    private suspend fun handleSignMessages(
        request: SignMessagesRequest,
        startedAt: Long,
        generation: Long,
        sessionId: String,
        protocolHandle: ProtocolEventHandle?,
        requestSummary: Map<String, String>,
    ) {
        if (!isCurrentGeneration(generation)) {
            request.completeWithDecline()
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_session_generation"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "session_authorization_inactive"),
            )
            return
        }

        val payloads = request.payloads
        val addresses = request.addresses

        if (NetworkPolicy.evaluate(request.chain) !is NetworkDecision.Allowed ||
            !LabAuthorizationPolicy.isCurrentAuthorizationScope(request.authorizationScope)
        ) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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

        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "authorization_revoked_before_approval"),
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
            completePersistentProtocol(
                handle = protocolHandle,
                outcome = ProtocolOutcome.CANCELLED,
                failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = mapOf("result" to "stale_during_approval"),
            )
            return
        }
        if (!isAuthorizationActive(generation)) {
            request.completeWithAuthorizationNotValid()
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                responseSummary = mapOf("result" to "authorization_revoked_during_approval"),
            )
            return
        }

        if (approval !is ApprovalDecision.Approved) {
            request.completeWithDecline()
            record(MwaSessionEvent.SIGN_MESSAGES_REJECTED)
            recordMigratedSigningProtocol(
                handle = protocolHandle,
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
            if (!isAuthorizationActive(generation)) {
                request.completeWithAuthorizationNotValid()
                recordMigratedSigningProtocol(
                    handle = protocolHandle,
                    method = ProtocolMethod.SIGN_MESSAGES,
                    startedAt = startedAt,
                    outcome = ProtocolOutcome.FAILURE,
                    protocolErrorCode = ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                    responseSummary = mapOf("result" to "authorization_revoked_after_signing"),
                )
                return
            }
            request.completeWithSignedPayloads(signedPayloadArray)
            record(MwaSessionEvent.SIGN_MESSAGES_SUCCEEDED)
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.SUCCESS,
                requestSummary = requestSummary,
                responseSummary = mapOf(
                    "result" to "signed",
                    "signed_payload_count" to signedPayloadArray.size.toString(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            request.completeWithInternalError(IllegalStateException("Lab signing failed"))
            recordMigratedSigningProtocol(
                handle = protocolHandle,
                method = ProtocolMethod.SIGN_MESSAGES,
                startedAt = startedAt,
                outcome = ProtocolOutcome.FAILURE,
                failureSource = ProtocolFailureSource.UNKNOWN,
                requestSummary = requestSummary,
                responseSummary = mapOf("result" to "signing_failed"),
            )
        }
    }

    private suspend fun recordMigratedSigningProtocol(
        handle: ProtocolEventHandle?,
        method: ProtocolMethod,
        startedAt: Long,
        outcome: ProtocolOutcome,
        protocolErrorCode: Int? = null,
        failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
        requestSummary: Map<String, String> = emptyMap(),
        responseSummary: Map<String, String> = emptyMap(),
    ) {
        completePersistentProtocol(
            handle = handle,
            outcome = outcome,
            protocolErrorCode = protocolErrorCode,
            failureSource = failureSource,
            responseSummary = responseSummary,
        )
        recordProtocol(
            method = method,
            startedAt = startedAt,
            outcome = outcome,
            protocolErrorCode = protocolErrorCode,
            failureSource = failureSource,
            requestSummary = requestSummary,
            responseSummary = responseSummary,
        )
    }

    private fun beginPersistentProtocol(
        sessionId: String,
        method: ProtocolMethod,
        requestSummary: Map<String, String> = emptyMap(),
    ): ProtocolEventHandle? = try {
        runBlocking(Dispatchers.IO) {
            protocolRecorder.begin(
                sessionId = sessionId,
                method = method,
                requestSummary = requestSummary,
            )
        }
    } catch (_: Exception) {
        record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "protocol_begin_${method.wireName}")
        null
    }

    private suspend fun completePersistentProtocol(
        handle: ProtocolEventHandle?,
        outcome: ProtocolOutcome,
        protocolErrorCode: Int? = null,
        failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
        responseSummary: Map<String, String> = emptyMap(),
    ) {
        if (handle == null) return
        val result = try {
            protocolRecorder.complete(
                handle = handle,
                outcome = outcome,
                protocolErrorCode = protocolErrorCode,
                failureSource = failureSource,
                responseSummary = responseSummary,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            record(
                MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED,
                "protocol_complete_${handle.method.wireName}",
            )
            return
        }
        transactionDiagnosticSettlement.completed(result)
        simulationDiagnosticSettlement.completed(result)
        if (result is ProtocolRecorder.CompletionResult.PersistenceFailed) {
            record(
                MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED,
                "protocol_complete_${handle.method.wireName}",
            )
        }
    }

    private fun completePersistentProtocolBlocking(
        handle: ProtocolEventHandle?,
        outcome: ProtocolOutcome,
        protocolErrorCode: Int? = null,
        failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
        responseSummary: Map<String, String> = emptyMap(),
    ) {
        runBlocking(Dispatchers.IO) {
            completePersistentProtocol(
                handle = handle,
                outcome = outcome,
                protocolErrorCode = protocolErrorCode,
                failureSource = failureSource,
                responseSummary = responseSummary,
            )
        }
    }

    private fun createPersistentSession(sessionId: String): SessionLifecycleCoordinator.PersistenceResult {
        val result = runBlocking(Dispatchers.IO) {
            sessionLifecycleCoordinator.createSession(sessionId)
        }
        if (result is SessionLifecycleCoordinator.PersistenceResult.PersistenceFailed) {
            record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "session_create")
        }
        return result
    }

    private fun captureSessionCapabilities(sessionId: String) {
        try {
            runBlocking(Dispatchers.IO) {
                capabilitySnapshotRepository.recordSnapshot(
                    MwaCapabilityProfile.snapshotForSession(
                        sessionId = sessionId,
                        capturedAtEpochMillis = System.currentTimeMillis().coerceAtLeast(0L),
                        walletConfig = walletConfig,
                    ),
                )
            }
        } catch (_: Exception) {
            // This synchronous diagnostic boundary must not change scenario start,
            // authorization, or signing. Never log exception text or configuration.
            runCatching {
                record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "capability_snapshot")
            }
        }
    }

    private fun finishPersistentSession(
        sessionId: String,
        closeReason: SessionCloseReason,
    ) {
        val result = runBlocking(Dispatchers.IO) {
            sessionLifecycleCoordinator.finishSession(sessionId, closeReason)
        }
        if (result is SessionLifecycleCoordinator.PersistenceResult.PersistenceFailed) {
            record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "session_finish")
        }
    }

    private fun updatePersistentDappIdentity(sessionId: String, identityName: String?) {
        val result = runBlocking(Dispatchers.IO) {
            sessionLifecycleCoordinator.updateDappIdentity(sessionId, identityName)
        }
        if (result is SessionLifecycleCoordinator.PersistenceResult.PersistenceFailed) {
            record(MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED, "dapp_identity")
        }
    }

    private fun clearActivePersistentSessionIfMatches(sessionId: String) {
        synchronized(lock) {
            if (activePersistentSessionId == sessionId) {
                activePersistentSessionId = null
            }
        }
    }

    private fun markAuthorizationActive(generation: Long) {
        if (isCurrentGeneration(generation)) {
            activeAuthorizationGeneration.set(generation)
        }
    }

    private fun invalidateAuthorization(generation: Long) {
        activeAuthorizationGeneration.compareAndSet(
            generation,
            NO_AUTHORIZATION_GENERATION,
        )
    }

    private fun isAuthorizationActive(generation: Long): Boolean =
        isCurrentGeneration(generation) &&
            activeAuthorizationGeneration.get() == generation

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
        payloads.take(MAX_PERSISTED_PAYLOAD_METADATA).forEachIndexed { index, payload ->
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
        private const val NO_AUTHORIZATION_GENERATION = -1L
        private const val REQUIRED_SCHEME = "solana-wallet"
        private const val AUTH_ISSUER_NAME = "mwa-lab-phase1"
        private const val MAX_MESSAGE_BYTES = 64 * 1024
        private const val MAX_PERSISTED_PAYLOAD_METADATA = 10
    }
}
