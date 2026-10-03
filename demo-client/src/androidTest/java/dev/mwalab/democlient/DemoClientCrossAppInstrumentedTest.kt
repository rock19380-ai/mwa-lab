package dev.mwalab.democlient

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.solana.mobilewalletadapter.common.ProtocolContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoClientCrossAppInstrumentedTest {
    private companion object {
        const val WALLET_PACKAGE = "dev.mwalab"
    }

    @Test
    fun canonicalSequenceIsRealCrossPackageAndRepeatable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("dev.mwalab.democlient", context.packageName)

        val first = runCanonicalWithExplicitAuthorization(context)
        val second = runCanonicalWithExplicitAuthorization(context)

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

    private fun runCanonicalWithExplicitAuthorization(
        context: android.content.Context,
    ): DemoRunResult {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<DemoRunResult> {
                DemoClientRunner(context).runCanonical()
            }
            clickWalletAuthorizationApproval()
            future.get(45, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun clickWalletAuthorizationApproval() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == WALLET_PACKAGE) {
                val tagged = findNode(root) { node ->
                    node.viewIdResourceName?.endsWith("authorization-approve") == true
                }
                if (clickNodeOrClickableAncestor(tagged)) return

                val textMatches = root.findAccessibilityNodeInfosByText("APPROVE")
                for (node in textMatches) {
                    if (clickNodeOrClickableAncestor(node)) return
                }
            }
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for clickable MWA Lab authorization approval control",
        )
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            findNode(child, predicate)?.let { return it }
        }
        return null
    }

    private fun clickNodeOrClickableAncestor(start: AccessibilityNodeInfo?): Boolean {
        var current = start
        while (current != null) {
            if (
                current.isEnabled &&
                current.isClickable &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
