package dev.mwalab.democlient

import com.solana.mobilewalletadapter.common.ProtocolContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Phase6AcceptanceScenarioTest {
    @Test fun everyP0ScenarioHasTheCorrectClientOperationAndWireExpectation() {
        val expected = mapOf(
            Phase6AcceptanceScenario.AUTH_REJECT to (Phase6AcceptanceScenario.Operation.AUTHORIZE to ProtocolContract.ERROR_AUTHORIZATION_FAILED),
            Phase6AcceptanceScenario.UNSUPPORTED_CHAIN to (Phase6AcceptanceScenario.Operation.AUTHORIZE to ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED),
            Phase6AcceptanceScenario.SIGN_REJECT to (Phase6AcceptanceScenario.Operation.SIGN_MESSAGE to ProtocolContract.ERROR_NOT_SIGNED),
            Phase6AcceptanceScenario.SIGN_AND_SEND_REJECT to (Phase6AcceptanceScenario.Operation.SIGN_AND_SEND to ProtocolContract.ERROR_NOT_SIGNED),
            Phase6AcceptanceScenario.INVALID_PAYLOAD to (Phase6AcceptanceScenario.Operation.SIGN_MESSAGE to ProtocolContract.ERROR_INVALID_PAYLOADS),
            Phase6AcceptanceScenario.TOO_MANY_PAYLOADS to (Phase6AcceptanceScenario.Operation.SIGN_MESSAGE to ProtocolContract.ERROR_TOO_MANY_PAYLOADS),
            Phase6AcceptanceScenario.STALE_BLOCKHASH to (Phase6AcceptanceScenario.Operation.SIGN_TRANSACTION to ProtocolContract.ERROR_INVALID_PAYLOADS),
            Phase6AcceptanceScenario.RPC_UNAVAILABLE to (Phase6AcceptanceScenario.Operation.SIGN_AND_SEND to ProtocolContract.ERROR_NOT_SUBMITTED),
            Phase6AcceptanceScenario.SUBMISSION_FAILURE to (Phase6AcceptanceScenario.Operation.SIGN_AND_SEND to ProtocolContract.ERROR_NOT_SUBMITTED),
        )
        assertEquals(Phase6AcceptanceScenario.entries.size - 2, expected.size)
        for ((scenario, contract) in expected) {
            assertEquals(contract.first, scenario.operation)
            assertEquals(contract.second, scenario.expectedCode)
            assertEquals(scenario, Phase6AcceptanceScenario.fromWireName(scenario.name))
        }
        assertNull(Phase6AcceptanceScenario.DELAY_5S.expectedCode)
        assertNull(Phase6AcceptanceScenario.NORMAL_REGRESSION.expectedCode)
        assertNull(Phase6AcceptanceScenario.fromWireName("set_wallet_fault"))
    }
}
