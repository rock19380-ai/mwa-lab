package dev.mwalab.ui.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.simulation.SimulationOutcome
import dev.mwalab.simulation.SimulationResult
import dev.mwalab.ui.components.StatusBadge

const val SIMULATION_PASS_WARNING =
    "Simulation passed on Devnet at the recorded context. This does not guarantee later signing, submission, confirmation, or unchanged chain state."

/** Text comes only from bounded safe domain values. Program logs remain untrusted observations. */
@Composable
fun SimulationResultContent(result: SimulationResult, tagPrefix: String) {
    var logsExpanded by rememberSaveable(result.simulationId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Simulation ${result.outcome.name}", Modifier.testTag("$tagPrefix-${result.outcome.name.lowercase()}"),
            style = MaterialTheme.typography.titleSmall)
        StatusBadge("SIMULATION ${result.outcome.name}", alert = result.outcome == SimulationOutcome.FAIL)
        Text("Diagnostic evidence only")
        Text("Simulation failure source: ${result.failureSource.name}")
        Text("Attempt ${result.attemptNumber} · ${result.durationMillis} ms")
        Text("Commitment: ${result.commitment}")
        result.error?.let { error ->
            Text("Error kind: ${error.kind.name}", Modifier.testTag("$tagPrefix-error-kind"))
            error.instructionIndex?.let { Text("Instruction index: $it") }
            error.instructionErrorKind?.let { Text("Instruction error: $it") }
            error.customProgramErrorCode?.let {
                Text("Numeric custom program error: $it", Modifier.testTag("$tagPrefix-custom-error"))
            }
        }
        result.availabilityReason?.let { Text("Availability: ${it.name}") }
        result.rpcErrorCode?.let { Text("JSON-RPC numeric error: $it") }
        result.contextSlot?.let { Text("Context slot: $it") }
        result.unitsConsumed?.let { Text("Compute units: $it", Modifier.testTag("$tagPrefix-compute-units")) }
        if (result.programLogs.isNotEmpty()) {
            OutlinedButton(onClick = { logsExpanded = !logsExpanded },
                modifier = Modifier.testTag("$tagPrefix-logs-expander")) {
                Text(if (logsExpanded) "Hide program logs" else "Show program logs (${result.programLogs.size})")
            }
            if (logsExpanded) {
                Text("Program logs are untrusted RPC observations.")
                result.programLogs.forEachIndexed { index, line ->
                    Text(line, Modifier.testTag("$tagPrefix-log-$index"), style = MaterialTheme.typography.bodySmall)
                }
                if (result.logsTruncated) Text("Program logs truncated by MWA Lab diagnostic limit.")
            }
        }
        if (result.outcome == SimulationOutcome.PASS) {
            Text(SIMULATION_PASS_WARNING, Modifier.testTag("$tagPrefix-pass-disclaimer"))
        }
    }
}
