package dev.mwalab.faults

import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.protocol.ProtocolMethod
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-checks the independent vector artifact against catalog and wire/side-effect contracts. */
class FaultVectorVerifierTest {
    private val vectors = Properties().apply {
        openFaultVectors().use(::load)
    }

    @Test fun vectorsCoverEveryStableIdWithoutDuplicatesOrUnknowns() {
        val ids = list("profiles")
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(FaultCatalog.profiles.map { it.id.stableId }.toSet(), ids.toSet())
        assertEquals(10, ids.size)
    }

    @Test fun vectorsMatchCatalogHooksMethodsDisplayAndProtocolCodes() {
        for (profile in FaultCatalog.profiles) {
            val id = profile.id.stableId
            assertEquals(profile.displayName, required("$id.display_name"))
            assertEquals(profile.targetMethods.map(ProtocolMethod::name).toSet(), list("$id.target_methods").toSet())
            assertEquals(profile.hook?.name.orEmpty(), required("$id.target_hook"))
            assertEquals(profile.expectedProtocolCode?.toString().orEmpty(), required("$id.expected_protocol_error_code"))
            assertEquals(profile.id.takeUnless { it == FaultId.NORMAL }?.stableId.orEmpty(),
                required("$id.expected_injected_fault_id"))
        }
    }

    @Test fun fixedWireAndSideEffectExpectationsRemainTruthful() {
        val errorCodes = mapOf(
            FaultId.AUTH_REJECT to "ERROR_AUTHORIZATION_FAILED" to ProtocolContract.ERROR_AUTHORIZATION_FAILED,
            FaultId.SIGN_REJECT to "ERROR_NOT_SIGNED" to ProtocolContract.ERROR_NOT_SIGNED,
            FaultId.UNSUPPORTED_CHAIN to "ERROR_CLUSTER_NOT_SUPPORTED" to ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED,
            FaultId.INVALID_PAYLOAD to "ERROR_INVALID_PAYLOADS" to ProtocolContract.ERROR_INVALID_PAYLOADS,
            FaultId.TOO_MANY_PAYLOADS to "ERROR_TOO_MANY_PAYLOADS" to ProtocolContract.ERROR_TOO_MANY_PAYLOADS,
            FaultId.RPC_UNAVAILABLE to "ERROR_NOT_SUBMITTED" to ProtocolContract.ERROR_NOT_SUBMITTED,
            FaultId.SUBMISSION_FAILURE to "ERROR_NOT_SUBMITTED" to ProtocolContract.ERROR_NOT_SUBMITTED,
            FaultId.STALE_BLOCKHASH to "ERROR_INVALID_PAYLOADS" to ProtocolContract.ERROR_INVALID_PAYLOADS,
        )
        for ((pair, code) in errorCodes) {
            val (id, name) = pair
            assertEquals(name, required("${id.stableId}.expected_protocol_error_name"))
            assertEquals(code.toString(), required("${id.stableId}.expected_protocol_error_code"))
            assertEquals("FAILURE", required("${id.stableId}.expected_outcome"))
            assertEquals("INJECTED", required("${id.stableId}.expected_failure_source"))
        }
        for (id in listOf(FaultId.NORMAL, FaultId.DELAY_5S)) {
            assertEquals("NORMAL_FLOW", required("${id.stableId}.expected_outcome"))
            assertEquals("NORMAL_FLOW", required("${id.stableId}.expected_failure_source"))
            assertEquals("", required("${id.stableId}.expected_protocol_error_name"))
        }
        assertEquals("5000", required("FAULT_DELAY_5S.delay_ms"))
        for (id in FaultId.entries - FaultId.NORMAL - FaultId.DELAY_5S) {
            val stable = id.stableId
            val signing = if (id == FaultId.RPC_UNAVAILABLE || id == FaultId.SUBMISSION_FAILURE) "YES" else "NO"
            assertEquals(signing, required("$stable.signing_expected"))
            assertEquals("NO", required("$stable.submission_expected"))
            assertEquals("NO", required("$stable.send_transaction_expected"))
            assertEquals(if (id == FaultId.RPC_UNAVAILABLE || id == FaultId.SUBMISSION_FAILURE) "YES"
                else if (id == FaultId.SIGN_REJECT) "METHOD_DEPENDENT" else "NO",
                required("$stable.blockhash_rpc_expected"))
        }
        assertEquals("completeWithInvalidSignatures",
            required("FAULT_INVALID_PAYLOAD.callback.SIGN_AND_SEND_TRANSACTIONS"))
        assertEquals("completeWithInvalidPayloads",
            required("FAULT_INVALID_PAYLOAD.callback.SIGN_MESSAGES"))
        assertEquals("completeWithNotSubmitted",
            required("FAULT_RPC_UNAVAILABLE.callback.SIGN_AND_SEND_TRANSACTIONS"))
        assertEquals("completeWithNotSubmitted",
            required("FAULT_SUBMISSION_FAILURE.callback.SIGN_AND_SEND_TRANSACTIONS"))
    }

    @Test fun everyFixedFailureVectorHasOnlyDeclaredCallbackMethods() {
        for (profile in FaultCatalog.profiles.filter { it.expectedProtocolCode != null }) {
            val prefix = "${profile.id.stableId}.callback."
            val declared = vectors.stringPropertyNames().filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix) }.toSet()
            assertEquals(profile.targetMethods.map { it.name }.toSet(), declared)
            assertTrue(declared.all { required(prefix + it).startsWith("completeWith") })
        }
    }

    private fun openFaultVectors() = run {
        val configured = System.getProperty("mwalab.phase6.faultVectorsPath")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
        val path = when {
            configured != null && Files.isRegularFile(configured) -> configured
            Files.isRegularFile(Path.of("test-vectors/faults/faults.properties")) ->
                Path.of("test-vectors/faults/faults.properties")
            Files.isRegularFile(Path.of("../test-vectors/faults/faults.properties")) ->
                Path.of("../test-vectors/faults/faults.properties")
            else -> error(
                "Phase 6 fault vectors not found; " +
                    "mwalab.phase6.faultVectorsPath=${configured ?: "<unset>"}"
            )
        }
        Files.newInputStream(path)
    }

    private fun required(key: String) = checkNotNull(vectors.getProperty(key)) { "Missing vector key $key" }
    private fun list(key: String) = required(key).split(',').filter { it.isNotEmpty() }
}
