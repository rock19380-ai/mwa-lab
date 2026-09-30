package dev.mwalab.ui.transaction

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import dev.mwalab.storage.*
import dev.mwalab.transaction.*
import dev.mwalab.simulation.*
import dev.mwalab.ui.sessions.*
import dev.mwalab.ui.theme.MWALabTheme
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionInspectorUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val inspector = TransactionInspector()
    private val sessionId = "inspector-ui"
    private val eventId = "$sessionId:1"

    @Test fun legacyOverviewAndStaticAccountPrivilegesRemainInspectableOnSmallScreen() {
        show(inspect(TransactionApprovalTestVectors.systemTransfer()))
        open()
        scroll("Overview")
        scroll("Required signer count: 1")
        scroll("Recent blockhash:", substring = true)
        scroll("MWA Lab payload fingerprint (SHA-256):", substring = true)
        screenshot("overview")
        scroll("Accounts")
        scroll("Account #0")
        scroll("Signer · Writable · Fee payer")
        scroll("Not signer · Read-only")
        scroll("Raw metadata")
        scroll("Header read-only unsigned accounts: 1")
    }

    @Test fun systemTransferDisplaysVerifiedExactLamportsAndDevnetSol() {
        show(inspect(TransactionApprovalTestVectors.systemTransfer()))
        open()
        scroll("System Program")
        scroll("Operation: Transfer")
        scroll("Lamports: 10000000")
        scroll("DEVNET SOL: 0.01")
        screenshot("system-transfer")
    }

    @Test fun memoShowsOnlySanitizedStoredMetadata() {
        val bytes = "MEMO_UI_SECRET_SENTINEL".toByteArray()
        show(inspect(wire(KnownProgram.MEMO.programId, bytes, emptyList(), keys = 2)))
        open()
        scroll("Memo Program")
        scroll("Memo display status: displayable")
        scroll("Memo text was not retained.", substring = true)
        scroll("Instruction data: ${bytes.size} bytes")
        compose.onAllNodesWithText("MEMO_UI_SECRET_SENTINEL", substring = true).assertCountEquals(0)
    }

    @Test fun splTransferAndCheckedShowRawWireFieldsOnly() {
        val amount = byteArrayOf(0x40, 0x42, 0x0f, 0, 0, 0, 0, 0)
        show(inspect(wire(KnownProgram.SPL_TOKEN.programId, byteArrayOf(3) + amount, listOf(0, 1, 2)), 0),
            inspect(wire(KnownProgram.SPL_TOKEN.programId, byteArrayOf(12) + amount + byteArrayOf(6), listOf(0, 1, 2, 3), keys = 5), 1))
        open(1)
        scroll("SPL Token Program")
        scroll("Raw amount: 1000000")
        scroll("Authority:", substring = true)
        open(2)
        scroll("Operation: TransferChecked")
        scroll("Mint:", substring = true)
        scroll("Declared decimals (wire metadata): 6")
        compose.onAllNodesWithText("USD", substring = true).assertCountEquals(0)
    }

    @Test fun unknownProgramShowsRealIdReferencesAndDataLengthHash() {
        val summary = inspect(TransactionApprovalTestVectors.systemTransfer(knownProgram = false))
        show(summary); open()
        scroll("Unknown Program")
        scroll("Program ID: ${summary.instructions!!.single().programId}")
        scroll("Unknown semantics")
        scroll("Instruction data: 12 bytes")
        scroll("Instruction data SHA-256: ${summary.instructions.single().dataSha256}")
        screenshot("unknown-program")
        compose.onAllNodesWithText("Operation: Transfer").assertCountEquals(0)
    }

    @Test fun versionZeroIsPartialAndLoadedAccountReferencesRemainUnavailable() {
        val summary = inspect(wire(KnownProgram.SYSTEM.programId, byteArrayOf(2, 0, 0, 0) + ByteArray(8),
            listOf(0, 3), keys = 3, versioned = true, lookup = true))
        show(summary); open()
        scroll("Inspection status: Partial inspection")
        scroll("Version: v0")
        scroll("v0 signing is not supported.", substring = true)
        scroll("Loaded lookup-table accounts: unavailable; no network resolution was performed.")
        scroll("#3 → Unavailable (unresolved lookup account)")
        scroll("Decoded operation unavailable: unresolved accounts")
        screenshot("v0-partial")
        compose.onAllNodesWithText("Operation: Transfer").assertCountEquals(0)
    }

    @Test fun malformedDiagnosticsShowErrorAndUnavailableStructureWithoutHidingEvent() {
        show(inspect(byteArrayOf()))
        open()
        scroll("Inspection status: Malformed transaction")
        scroll("Fee payer: Unavailable")
        scroll("Structural error: empty payload at byte 0")
        scroll("Static accounts unavailable for this inspection.")
        scroll("Instructions unavailable for this inspection.")
        scroll("#1 SIGN_TRANSACTIONS")
        compose.onNodeWithText("SUCCESS · 40 ms").assertExists()
    }

    @Test fun multiplePayloadsRemainSeparateInStableOrder() {
        show(inspect(TransactionApprovalTestVectors.systemTransfer(), 0),
            inspect(TransactionApprovalTestVectors.systemTransfer(knownProgram = false), 1))
        scroll("Transaction 1")
        scroll("Transaction 2")
        open(2)
        scroll("Unknown Program")
        scroll("Payload index: 1 (zero-based)")
        open(1)
        scroll("System Program")
        scroll("Payload index: 0 (zero-based)")
    }

    @Test fun diagnosticsAreUnderTheMatchingEventAndMessagesHaveNoInspector() {
        val first = event()
        val message = event(2).copy(method = ProtocolMethod.SIGN_MESSAGES)
        val second = event(3).copy(method = ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS)
        val summary = SessionSummary(MwaSession(sessionId, 100), listOf(second, message, first))
        val state = SessionDetailUiState.Ready(summary, transactions = mapOf(
            first.eventId to SessionTransactionUiState.Recorded(listOf(inspect(TransactionApprovalTestVectors.systemTransfer()))),
            second.eventId to SessionTransactionUiState.Recorded(listOf(inspect(byteArrayOf(), eventId = second.eventId)))))
        compose.setContent { MWALabTheme { SessionDetailScreen(state, {}, {}) } }
        scroll("TRANSACTION DIAGNOSTICS · event #1")
        scroll("#2 SIGN_MESSAGES")
        compose.onAllNodesWithText("TRANSACTION DIAGNOSTICS · event #2").assertCountEquals(0)
        scroll("TRANSACTION DIAGNOSTICS · event #3")
        compose.onNodeWithTag("transaction-${second.eventId}-0").assertExists()
    }

    @Test fun historicalSessionHasTruthfulAbsenceAndIntactProtocolTimeline() {
        compose.setContent { MWALabTheme {
            SessionDetailScreen(SessionDetailUiState.Ready(SessionSummary(MwaSession(sessionId, 100), listOf(event()))), {}, {})
        } }
        scroll("Capability snapshot was not recorded for this session.")
        scroll("#1 SIGN_TRANSACTIONS")
        scroll("Transaction diagnostics were not recorded for this event.")
        compose.onAllNodesWithText("Inspect transaction 1").assertCountEquals(0)
    }

    @Test fun diagnosticLoadingAndReadErrorHaveIndependentRetryAndTimeline() {
        var retries = 0
        var state by mutableStateOf(SessionTransactionUiState.Loading as SessionTransactionUiState)
        compose.setContent { MWALabTheme {
            SessionDetailScreen(ready(state), {}, { error("Timeline retry is separate") },
                onRetryTransactions = { assertEquals(eventId, it); retries++ })
        } }
        scroll("Loading recorded transaction diagnostics…")
        scroll("#1 SIGN_TRANSACTIONS")
        compose.runOnIdle { state = SessionTransactionUiState.Unavailable }
        scroll("Retry transaction diagnostics")
        compose.onNodeWithText("Retry transaction diagnostics").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        scroll("#1 SIGN_TRANSACTIONS")
        compose.runOnIdle { state = SessionTransactionUiState.Missing }
        scroll("Transaction diagnostics were not recorded for this event.")
    }

    @Test fun expansionSurvivesRestorationAndRecompositionWithLongLazyInstructionLists() {
        val restore = StateRestorationTester(compose)
        var landscape by mutableStateOf(false)
        val summary = inspect(wire(KnownProgram.SYSTEM.programId, byteArrayOf(1, 0, 0, 0),
            emptyList(), keys = 2, count = 80))
        restore.setContent { MWALabTheme {
            Box(Modifier.width(if (landscape) 480.dp else 320.dp).height(if (landscape) 320.dp else 480.dp)) {
                SessionDetailScreen(ready(SessionTransactionUiState.Recorded(listOf(summary))), {}, {})
            }
        } }
        open()
        scroll("Instruction #80")
        scroll("Program ID: ${KnownProgram.SYSTEM.programId}")
        compose.runOnIdle { landscape = true }
        scroll("Instruction #80")
        restore.emulateSavedInstanceStateRestore()
        scroll("Collapse transaction 1")
        scroll("Raw metadata")
    }

    @Test fun simulationAttemptsStayBelowParentAndRetryDoesNotHideTimeline() {
        val ref = SimulationTargetRef(sessionId, eventId, "request", 0, "a".repeat(64))
        val first = SimulationResult("sim-first", ref, 1, 1, 2, 1,
            SimulationOutcome.FAIL, ProtocolFailureSource.SIMULATION, "processed", 9,
            SimulationErrorSummary(SimulationErrorKind.BLOCKHASH_NOT_FOUND))
        val latest = SimulationResult("sim-latest", ref, 2, 2, 3, 1,
            SimulationOutcome.PASS, ProtocolFailureSource.NONE, "processed", 10,
            unitsConsumed = 12, logs = BoundedLogs(listOf("Program log: bounded"), false))
        var diagnostics by mutableStateOf<SessionSimulationUiState>(
            SessionSimulationUiState.Recorded(listOf(first, latest)))
        var retries = 0
        compose.setContent { MWALabTheme {
            SessionDetailScreen(SessionDetailUiState.Ready(
                SessionSummary(MwaSession(sessionId, 100), listOf(event())),
                simulations = mapOf(eventId to diagnostics)), {}, {},
                onRetrySimulations = { assertEquals(eventId, it); retries++ })
        } }
        scroll("#1 SIGN_TRANSACTIONS")
        scroll("SIMULATION DIAGNOSTICS · event #1")
        scroll("Payload 1 · attempt 2 · LATEST")
        compose.onNodeWithTag("persisted-simulation-$eventId-0-2-logs-expander")
            .performScrollTo().performClick()
        scroll(SimulationLimits.REDACTED_LOG)
        scroll("Simulation passed on Devnet", substring = true)
        compose.runOnIdle { diagnostics = SessionSimulationUiState.Unavailable }
        scroll("Retry simulation diagnostics")
        compose.onNodeWithText("Retry simulation diagnostics").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        scroll("#1 SIGN_TRANSACTIONS")
        compose.runOnIdle { diagnostics = SessionSimulationUiState.Missing }
        scroll("Simulation was not recorded for this event.")
    }

    @Test fun reopenedRoomDiagnosticsReachSessionDetailThroughTheRepositoryAndViewModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase4-10-ui-${UUID.randomUUID()}.db"
        val summary = inspect(TransactionApprovalTestVectors.systemTransfer())
        val db = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        runBlocking {
            val repo = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
            repo.createSession(MwaSession(sessionId, 100))
            repo.recordProtocolEvent(event().copy(requestSummary = mapOf(
                "payload_count" to "1", "payload_0_sha256" to summary.fingerprintSha256,
                "payload_0_length" to summary.wireLength.toString())))
            RoomTransactionDiagnosticRepository(db.transactionDiagnosticDao()).recordForEvent(sessionId, eventId, listOf(summary))
            RoomSimulationRepository(db.simulationResultDao()).recordForEvent(sessionId, eventId,
                listOf(SimulationResult("reopened-simulation", SimulationTargetRef(
                    sessionId, eventId, "request", 0, summary.fingerprintSha256),
                    1, 1, 2, 1, SimulationOutcome.PASS, ProtocolFailureSource.NONE, "processed", 11)))
        }
        db.close()
        val reopened = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val model = SessionDetailViewModel(RoomSessionRepository(reopened.sessionDao(), reopened.protocolEventDao()),
                RoomCapabilitySnapshotRepository(reopened.capabilitySnapshotDao()), scope,
                RoomTransactionDiagnosticRepository(reopened.transactionDiagnosticDao()),
                RoomSimulationRepository(reopened.simulationResultDao()))
            model.selectSession(sessionId)
            compose.setContent { MWALabTheme {
                val state by model.state.collectAsState()
                SessionDetailScreen(state, {}, model::retry, model::retryCapabilities, model::retryTransactions)
            } }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("SESSION ACTIVE", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            open()
            scroll("Lamports: 10000000")
            scroll("MWA Lab payload fingerprint (SHA-256): ${summary.fingerprintSha256}")
            scroll("SIMULATION DIAGNOSTICS · event #1")
            scroll("Simulation PASS")
            scroll("Simulation passed on Devnet", substring = true)
        } finally {
            scope.cancel()
            runBlocking { scope.coroutineContext[Job]!!.join() }
            reopened.close(); context.deleteDatabase(name)
        }
    }

    private fun show(vararg summaries: TransactionSummary) {
        compose.setContent { MWALabTheme {
            Box(Modifier.width(320.dp).height(480.dp)) {
                SessionDetailScreen(ready(SessionTransactionUiState.Recorded(summaries.toList())), {}, {})
            }
        } }
    }
    private fun ready(state: SessionTransactionUiState) = SessionDetailUiState.Ready(
        SessionSummary(MwaSession(sessionId, 100), listOf(event())), transactions = mapOf(eventId to state))
    private fun event(sequence: Long = 1) = ProtocolEvent(sessionId, "$sessionId:$sequence", sequence,
        ProtocolMethod.SIGN_TRANSACTIONS, 110, 150, ProtocolOutcome.SUCCESS)
    private fun inspect(bytes: ByteArray, index: Int = 0, eventId: String = this.eventId) =
        inspector.inspect(bytes, index, TransactionDiagnosticBinding(sessionId, eventId))
    private fun open(number: Int = 1) {
        scroll("Inspect transaction $number")
        compose.onNodeWithText("Inspect transaction $number").performClick()
    }
    private fun scroll(text: String, substring: Boolean = false) {
        compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText(text, substring = substring))
        compose.onAllNodesWithText(text, substring = substring).onFirst().assertIsDisplayed()
    }

    private fun wire(program: String, data: ByteArray, references: List<Int>, keys: Int = 4,
        count: Int = 1, versioned: Boolean = false, lookup: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(1); out.write(ByteArray(64))
        if (versioned) out.write(0x80)
        out.write(1); out.write(0); out.write(1); out.write(keys)
        repeat(keys - 1) { out.write(ByteArray(32) { _ -> (0x11 * (it + 1)).toByte() }) }
        out.write(publicKey(program)); out.write(ByteArray(32) { 0x77 })
        out.write(count)
        repeat(count) {
            out.write(keys - 1); out.write(references.size); references.forEach(out::write)
            require(data.size < 128); out.write(data.size); out.write(data)
        }
        if (versioned) {
            out.write(if (lookup) 1 else 0)
            if (lookup) { out.write(ByteArray(32) { 0x66 }); out.write(1); out.write(7); out.write(0) }
        }
        return out.toByteArray()
    }

    private fun publicKey(value: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var number = BigInteger.ZERO
        value.forEach { number = number * BigInteger.valueOf(58) + BigInteger.valueOf(alphabet.indexOf(it).toLong()) }
        val body = if (number == BigInteger.ZERO) byteArrayOf() else number.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return (ByteArray(value.takeWhile { it == '1' }.length) + body).also { require(it.size == 32) }
    }

    // Public deterministic fixture evidence only; this is a test helper, not product report export.
    private fun screenshot(label: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "phase4-10-$label-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MWA-Lab-Phase4-10")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
