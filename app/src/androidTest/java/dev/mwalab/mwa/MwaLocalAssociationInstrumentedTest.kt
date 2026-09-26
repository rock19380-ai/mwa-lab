package dev.mwalab.mwa

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MwaLocalAssociationInstrumentedTest {
    @Test
    fun realLocalAssociationIsDiscoverableEstablishesDispatchesAndCloses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        MwaSessionEvidenceStore.resetForTest()

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

        // Batch C proves that a real protocol request reaches MwaSessionHost.
        // Authorization success itself is Phase 1.7, so this Devnet request is
        // deliberately declined with the normal protocol authorization error.
        val authorization = client.authorize(
            Uri.parse("https://phase1-client.invalid"),
            Uri.parse("icon.png"),
            "MWA Lab Phase 1 deterministic client",
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            null,
            null,
            null,
            null,
        )

        val authorizationFailure = runCatching {
            authorization.get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()

        assertNotNull(
            "Batch C authorization must be policy-declined until Phase 1.7",
            authorizationFailure,
        )

        awaitEvent(MwaSessionEvent.AUTHORIZE_REQUEST)
        awaitEvent(MwaSessionEvent.AUTHORIZE_DECLINED_PENDING_PHASE_1_7)

        localAssociation.close().get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.SERVING_COMPLETE)

        assertTrue(
            "Association token/public key must never be copied into evidence details",
            MwaSessionEvidenceStore.snapshot().none { event ->
                event.detail?.contains("association_token", ignoreCase = true) == true ||
                    event.detail?.contains("auth_token", ignoreCase = true) == true
            },
        )
    }

    private fun awaitEvent(expected: MwaSessionEvent) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (MwaSessionEvidenceStore.snapshot().any { it.event == expected }) {
                return
            }
            Thread.sleep(50)
        }

        throw AssertionError(
            "Timed out waiting for $expected; observed=" +
                MwaSessionEvidenceStore.snapshot().joinToString { it.event.name },
        )
    }
}
