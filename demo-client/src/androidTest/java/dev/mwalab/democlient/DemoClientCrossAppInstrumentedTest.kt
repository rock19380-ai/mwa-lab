package dev.mwalab.democlient

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.common.ProtocolContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoClientCrossAppInstrumentedTest {
    @Test
    fun canonicalSequenceIsRealCrossPackageAndRepeatable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("dev.mwalab.democlient", context.packageName)

        val first = DemoClientRunner(context).runCanonical()
        val second = DemoClientRunner(context).runCanonical()

        assertEquals("dev.mwalab", first.walletPackage)
        assertEquals("dev.mwalab", second.walletPackage)
        assertFalse(first.walletPackage == context.packageName)

        assertTrue(first.accountBase58.isNotBlank())
        assertEquals(first.accountBase58, second.accountBase58)

        assertEquals(10, first.maxTransactionsPerSigningRequest)
        assertEquals(10, first.maxMessagesPerSigningRequest)
        assertEquals(listOf("legacy"), first.supportedTransactionVersions)
        assertEquals(
            listOf(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS),
            first.optionalFeatures,
        )

        assertEquals(first.maxTransactionsPerSigningRequest, second.maxTransactionsPerSigningRequest)
        assertEquals(first.maxMessagesPerSigningRequest, second.maxMessagesPerSigningRequest)
        assertEquals(first.supportedTransactionVersions, second.supportedTransactionVersions)
        assertEquals(first.optionalFeatures, second.optionalFeatures)

        assertEquals(
            listOf(
                DemoStepName.CONNECTION,
                DemoStepName.AUTHORIZE,
                DemoStepName.CAPABILITIES,
                DemoStepName.DEAUTHORIZE,
            ),
            first.steps.map { it.step },
        )
        assertTrue(first.steps.all { it.passed })
        assertTrue(second.steps.all { it.passed })

        assertEquals(
            "MWA Lab Demo Client — FOR TESTING ONLY",
            DemoClientRunner.CLIENT_IDENTITY_NAME,
        )
    }
}
