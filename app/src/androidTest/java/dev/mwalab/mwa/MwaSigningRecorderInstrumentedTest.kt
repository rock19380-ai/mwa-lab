package dev.mwalab.mwa

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.approval.ApprovalState
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import dev.mwalab.protocol.ProtocolEvidenceStore
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MwaSigningRecorderInstrumentedTest {
    @Test
    fun signingFamilyPersistsCanonicalOrderedEventsWithoutRawPayloads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = MwaLabComposition.sessionRepository(context)
        val beforeIds = runBlocking {
            repository.observeSessions().first().map { it.session.id }.toSet()
        }
        MwaSessionEvidenceStore.resetForTest()
        ProtocolEvidenceStore.resetForTest()

        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val implicitIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session,
        )
        val explicitIntent = Intent(implicitIntent)
            .setClass(context, MobileWalletAdapterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(explicitIntent)
        val client = localAssociation.start().get(30, TimeUnit.SECONDS)
        assertNotNull(client)
        awaitEvent(MwaSessionEvent.SCENARIO_READY)
        awaitEvent(MwaSessionEvent.SERVING_CLIENTS)

        val identityName = "MWA Lab Phase 3 signing recorder ${System.nanoTime()}"
        val identityUri = Uri.parse("https://phase3-signing-recorder.invalid")
        val iconUri = Uri.parse("icon.png")

        val authorization = client.authorize(
            identityUri,
            iconUri,
            identityName,
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            null,
            null,
            null,
            null,
        ).get(10, TimeUnit.SECONDS)
        assertTrue(authorization.authToken.isNotEmpty())
        awaitEvent(MwaSessionEvent.AUTHORIZE_SUCCEEDED)

        val publicKey = authorization.accounts.single().publicKey
        val message = "phase3-signing-recorder-sensitive-message".toByteArray(StandardCharsets.UTF_8)
        val approval = approveNext(ProtocolMethod.SIGN_MESSAGES)
        @Suppress("DEPRECATION")
        val signedMessage = client.signMessages(
            arrayOf(message),
            arrayOf(publicKey),
        ).get(15, TimeUnit.SECONDS)
        assertTrue(approval.get(10, TimeUnit.SECONDS))
        assertEquals(1, signedMessage.signedPayloads.size)
        assertTrue(signedMessage.signedPayloads.single().size > message.size)
        assertArrayEquals(
            message,
            signedMessage.signedPayloads.single().copyOfRange(0, message.size),
        )
        awaitEvent(MwaSessionEvent.SIGN_MESSAGES_SUCCEEDED)

        // Non-empty malformed payloads traverse walletlib to the observable
        // callbacks but deterministically fail local legacy parsing before RPC.
        val malformedTransaction = byteArrayOf(1, 2, 3, 4, 5)

        @Suppress("DEPRECATION")
        val signTransactionsFailure = runCatching {
            client.signTransactions(arrayOf(malformedTransaction))
                .get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()
        assertNotNull("Malformed legacy sign_transactions must fail", signTransactionsFailure)
        awaitEvent(MwaSessionEvent.SIGN_TRANSACTIONS_INVALID)

        val signAndSendFailure = runCatching {
            client.signAndSendTransactions(
                arrayOf(malformedTransaction),
                null,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()
        assertNotNull("Malformed legacy sign_and_send must fail", signAndSendFailure)
        awaitEvent(MwaSessionEvent.SIGN_AND_SEND_INVALID)

        val active = awaitNewSession(repository, beforeIds)
        val recorded = awaitSession(repository, active.session.id) { summary ->
            summary.events.any { it.method == ProtocolMethod.SIGN_MESSAGES } &&
                summary.events.any { it.method == ProtocolMethod.SIGN_TRANSACTIONS } &&
                summary.events.any { it.method == ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS }
        }

        assertEquals(identityName, recorded.session.dappIdentityName)
        assertEquals(
            listOf(
                ProtocolMethod.AUTHORIZE,
                ProtocolMethod.SIGN_MESSAGES,
                ProtocolMethod.SIGN_TRANSACTIONS,
                ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
            ),
            recorded.events.map { it.method },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), recorded.events.map { it.sequence })
        assertEquals(
            recorded.events.mapIndexed { index, _ -> "${recorded.session.id}:${index + 1}" },
            recorded.events.map { it.eventId },
        )

        val messageEvent = recorded.events[1]
        assertEquals(ProtocolOutcome.SUCCESS, messageEvent.outcome)
        assertEquals(ProtocolFailureSource.NONE, messageEvent.failureSource)
        assertEquals("signed", messageEvent.responseSummary["result"])
        assertEquals("1", messageEvent.requestSummary["payload_count"])
        assertEquals(message.size.toString(), messageEvent.requestSummary["payload_0_length"])
        assertEquals(64, messageEvent.requestSummary["payload_0_sha256"]?.length)

        val signTransactionEvent = recorded.events[2]
        assertEquals(ProtocolOutcome.FAILURE, signTransactionEvent.outcome)
        assertEquals(ProtocolContract.ERROR_INVALID_PAYLOADS, signTransactionEvent.protocolErrorCode)
        assertEquals(ProtocolFailureSource.LOCAL_PARSER, signTransactionEvent.failureSource)
        assertEquals("invalid_legacy_transaction", signTransactionEvent.responseSummary["result"])

        val signAndSendEvent = recorded.events[3]
        assertEquals(ProtocolOutcome.FAILURE, signAndSendEvent.outcome)
        assertEquals(ProtocolContract.ERROR_INVALID_PAYLOADS, signAndSendEvent.protocolErrorCode)
        assertEquals(ProtocolFailureSource.LOCAL_PARSER, signAndSendEvent.failureSource)
        assertEquals("invalid_legacy_transaction", signAndSendEvent.responseSummary["result"])

        assertFalse(recorded.events.any { it.method == ProtocolMethod.GET_CAPABILITIES })
        assertTrue(recorded.events.all { it.completedAtEpochMillis >= it.startedAtEpochMillis })
        assertTrue(
            "Persistent signing summaries must contain hashes/lengths, never raw payload or private material",
            recorded.events.none { event ->
                (event.requestSummary + event.responseSummary).values.any { value ->
                    val normalized = value.lowercase()
                    normalized.contains("phase3-signing-recorder-sensitive-message") ||
                        normalized.contains("phase3-signing-recorder.invalid") ||
                        normalized.contains("auth_token") ||
                        normalized.contains("private_key") ||
                        normalized.contains("mnemonic") ||
                        normalized.contains("seed")
                }
            },
        )
        assertFalse(
            MwaSessionEvidenceStore.snapshot().any {
                it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED
            },
        )

        // The predecessor process-local mirror remains alive for Phase 2
        // regression compatibility, but Room is the Phase 3 product authority.
        val compatibility = ProtocolEvidenceStore.snapshot()
        assertTrue(compatibility.any { it.method == ProtocolMethod.SIGN_MESSAGES })
        assertTrue(compatibility.any { it.method == ProtocolMethod.SIGN_TRANSACTIONS })
        assertTrue(compatibility.any { it.method == ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS })

        localAssociation.close().get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.SERVING_COMPLETE)
    }

    private fun approveNext(method: ProtocolMethod) =
        Executors.newSingleThreadExecutor().let { executor ->
            val result = executor.submit<Boolean> {
                try {
                    val coordinator = MwaLabComposition.approvalCoordinator()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (System.nanoTime() < deadline) {
                        val state = coordinator.state.value
                        if (state is ApprovalState.Pending &&
                            state.request.method == method.wireName
                        ) {
                            return@submit coordinator.approve(state.request.requestId)
                        }
                        Thread.sleep(20)
                    }
                    false
                } finally {
                    executor.shutdown()
                }
            }
            result
        }

    private fun awaitNewSession(
        repository: SessionRepository,
        beforeIds: Set<String>,
    ): SessionSummary {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val sessions = runBlocking { repository.observeSessions().first() }
            sessions.firstOrNull { it.session.id !in beforeIds }?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for Phase 3 signing session")
    }

    private fun awaitSession(
        repository: SessionRepository,
        sessionId: String,
        predicate: (SessionSummary) -> Boolean,
    ): SessionSummary {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val session = runBlocking { repository.getSession(sessionId) }
            if (session != null && predicate(session)) return session
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for persistent signing events for $sessionId")
    }

    private fun awaitEvent(expected: MwaSessionEvent) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (MwaSessionEvidenceStore.snapshot().any { it.event == expected }) return
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for $expected; observed=" +
                MwaSessionEvidenceStore.snapshot().joinToString { it.event.name },
        )
    }
}
