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
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import dev.mwalab.protocol.ProtocolEvidenceStore
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MwaLocalAssociationInstrumentedTest {
    @Test
    fun phase1AuthorizeCapabilitiesDeauthorizeAndRevocationFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        MwaSessionEvidenceStore.resetForTest()
        ProtocolEvidenceStore.resetForTest()

        val expectedIdentity = runBlocking {
            MwaLabComposition.identityRepository(context).getOrCreate()
        }

        val localAssociation =
            LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

        val implicitIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session,
        )

        val resolvers = context.packageManager.queryIntentActivities(
            implicitIntent,
            0,
        )

        assertTrue(
            "MWA Lab association Activity must be discoverable for solana-wallet://",
            resolvers.any { info ->
                info.activityInfo.packageName == context.packageName &&
                    info.activityInfo.name == MobileWalletAdapterActivity::class.java.name
            },
        )

        val explicitIntent = Intent(implicitIntent)
            .setClass(context, MobileWalletAdapterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(explicitIntent)

        val client = localAssociation.start()
            .get(30, TimeUnit.SECONDS)

        assertNotNull(client)
        awaitEvent(MwaSessionEvent.SCENARIO_READY)
        awaitEvent(MwaSessionEvent.SERVING_CLIENTS)

        val capabilities = client.getCapabilities()
            .get(10, TimeUnit.SECONDS)

        assertEquals(10, capabilities.maxTransactionsPerSigningRequest)
        assertEquals(10, capabilities.maxMessagesPerSigningRequest)
        assertArrayEquals(
            arrayOf<Any>("legacy"),
            capabilities.supportedTransactionVersions,
        )
        assertArrayEquals(
            arrayOf(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS),
            capabilities.supportedOptionalFeatures,
        )

        val identityUri = Uri.parse("https://phase1-client.invalid")
        val iconUri = Uri.parse("icon.png")
        val identityName = "MWA Lab Phase 1 deterministic client"

        val mainnetFailure = runCatching {
            client.authorize(
                identityUri,
                iconUri,
                identityName,
                ProtocolContract.CHAIN_SOLANA_MAINNET,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()

        assertNotNull("Mainnet authorization must fail closed", mainnetFailure)
        awaitEvent(MwaSessionEvent.AUTHORIZE_CHAIN_REJECTED)

        val rejectionCountBeforeMissingChain =
            MwaSessionEvidenceStore.snapshot().count {
                it.event == MwaSessionEvent.AUTHORIZE_CHAIN_REJECTED
            }

        val missingChainFailure = runCatching {
            client.authorize(
                identityUri,
                iconUri,
                identityName,
                null,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()

        assertNotNull(
            "Missing-chain authorization must fail closed",
            missingChainFailure,
        )
        awaitEventCount(
            MwaSessionEvent.AUTHORIZE_CHAIN_REJECTED,
            rejectionCountBeforeMissingChain + 1,
        )

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
        assertEquals(1, authorization.accounts.size)
        assertArrayEquals(
            expectedIdentity.publicKeyBytes(),
            authorization.accounts.single().publicKey,
        )
        awaitEvent(MwaSessionEvent.AUTHORIZE_SUCCEEDED)

        val authToken = authorization.authToken

        client.deauthorize(authToken).get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.DEAUTHORIZED_COMPLETED)

        val revokedReuseFailure = runCatching {
            client.authorize(
                identityUri,
                iconUri,
                identityName,
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                authToken,
                null,
                arrayOf(expectedIdentity.publicKeyBytes()),
                null,
            ).get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()

        assertNotNull(
            "Revoked auth token must not be reusable",
            revokedReuseFailure,
        )

        val protocolEvidence = ProtocolEvidenceStore.snapshot()
        assertTrue(
            protocolEvidence.any {
                it.method == ProtocolMethod.AUTHORIZE &&
                    it.outcome == ProtocolOutcome.SUCCESS
            },
        )
        assertTrue(
            protocolEvidence.any {
                it.method == ProtocolMethod.AUTHORIZE &&
                    it.outcome == ProtocolOutcome.FAILURE
            },
        )
        assertTrue(
            protocolEvidence.any {
                it.method == ProtocolMethod.DEAUTHORIZE &&
                    it.outcome == ProtocolOutcome.SUCCESS
            },
        )
        assertTrue(
            "Structured evidence must not contain raw auth-token/private material",
            protocolEvidence.none { event ->
                (event.requestSummary + event.responseSummary)
                    .values
                    .any { value ->
                        val normalized = value.lowercase()
                        normalized.contains("auth_token") ||
                            normalized.contains("private_key") ||
                            normalized.contains("mnemonic") ||
                            normalized.contains("seed")
                    }
            },
        )

        localAssociation.close().get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.SERVING_COMPLETE)

        val events = MwaSessionEvidenceStore.snapshot()
        assertTrue(
            "Session evidence must never contain token/payload/private material",
            events.none { event ->
                val detail = event.detail.orEmpty().lowercase()
                detail.contains("auth_token") ||
                    detail.contains("association_token") ||
                    detail.contains("private") ||
                    detail.contains("seed") ||
                    detail.contains("mnemonic") ||
                    detail.contains("payload") ||
                    detail.contains("signature")
            },
        )
    }

    private fun awaitEvent(expected: MwaSessionEvent) {
        awaitEventCount(expected, 1)
    }

    private fun awaitEventCount(
        expected: MwaSessionEvent,
        minimumCount: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val count = MwaSessionEvidenceStore.snapshot().count {
                it.event == expected
            }
            if (count >= minimumCount) {
                return
            }
            Thread.sleep(50)
        }

        throw AssertionError(
            "Timed out waiting for $expected count >= $minimumCount; observed=" +
                MwaSessionEvidenceStore.snapshot().joinToString { it.event.name },
        )
    }
}
