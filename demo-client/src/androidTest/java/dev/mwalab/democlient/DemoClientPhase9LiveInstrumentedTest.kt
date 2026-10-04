package dev.mwalab.democlient

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoClientPhase9LiveInstrumentedTest {
    @Test
    fun normalSignAndSendConfirmsOnDevnet() {
        requireLiveOptIn()

        val result = runScenario(
            scenario = Phase2AcceptanceScenario.SIGN_AND_SEND_APPROVE,
            approveSigning = true,
        )

        assertTrue(result.passed)
        assertEquals(Phase2AcceptanceScenario.SIGN_AND_SEND_APPROVE, result.scenario)
        assertEquals(
            "approved_memo_transaction_submitted_and_confirmed_on_devnet",
            result.summary,
        )
    }

    @Test
    fun injectedSignRejectReturnsExpectedProtocolErrorWithoutSigningApproval() {
        requireLiveOptIn()

        val result = runScenario(
            scenario = Phase2AcceptanceScenario.SIGN_AND_SEND_REJECT,
            approveSigning = false,
        )

        assertTrue(result.passed)
        assertEquals(Phase2AcceptanceScenario.SIGN_AND_SEND_REJECT, result.scenario)
        assertEquals(
            "user_rejection_returned_error_not_signed_before_submission",
            result.summary,
        )
    }

    private fun requireLiveOptIn() {
        assumeTrue(
            "Live Devnet acceptance runs only when explicitly selected.",
            InstrumentationRegistry.getArguments().getString("mwa_phase9_live") == "1",
        )
    }

    private fun runScenario(
        scenario: Phase2AcceptanceScenario,
        approveSigning: Boolean,
    ): Phase2AcceptanceResult {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executor = Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<Phase2AcceptanceResult> {
                Phase2AcceptanceRunner(context).run(scenario)
            }

            clickAuthorizationApprove()

            if (approveSigning) {
                waitForWalletText("SIGNING APPROVAL", 45)
                clickWalletButton("APPROVE", 20)
            }

            future.get(if (approveSigning) 180 else 90, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun clickAuthorizationApprove() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)

        while (System.nanoTime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == WALLET_PACKAGE) {
                val tagged = findNode(root) { node ->
                    node.viewIdResourceName?.endsWith("authorization-approve") == true
                }
                if (clickNodeOrClickableAncestor(tagged)) return

                for (node in root.findAccessibilityNodeInfosByText("APPROVE")) {
                    if (clickNodeOrClickableAncestor(node)) return
                }
            }
            Thread.sleep(50)
        }

        throw AssertionError(
            "Timed out waiting for clickable MWA Lab authorization approval control",
        )
    }

    private fun waitForWalletText(text: String, timeoutSeconds: Long) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)

        while (System.nanoTime() < deadline) {
            val root = automation.rootInActiveWindow
            if (
                root != null &&
                root.packageName?.toString() == WALLET_PACKAGE &&
                root.findAccessibilityNodeInfosByText(text).isNotEmpty()
            ) {
                return
            }
            Thread.sleep(50)
        }

        throw AssertionError("Timed out waiting for MWA Lab text: $text")
    }

    private fun clickWalletButton(text: String, timeoutSeconds: Long) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)

        while (System.nanoTime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == WALLET_PACKAGE) {
                for (node in root.findAccessibilityNodeInfosByText(text)) {
                    if (clickNodeOrClickableAncestor(node)) return
                }
            }
            Thread.sleep(50)
        }

        throw AssertionError("Timed out waiting for clickable MWA Lab button: $text")
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

    private fun clickNodeOrClickableAncestor(
        start: AccessibilityNodeInfo?,
    ): Boolean {
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

    private companion object {
        const val WALLET_PACKAGE = "dev.mwalab"
    }
}
