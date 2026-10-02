package dev.mwalab.ui.faults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.faults.FaultId
import dev.mwalab.faults.FaultProfile
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.StatusBadge
import dev.mwalab.ui.sessions.protocolErrorText

/** A projection of the process-wide selection authority. No protocol decisions live here. */
@Composable
fun FaultLabScreen(selected: FaultProfile, onSelect: (FaultId) -> Unit) {
    val groups = listOf(
        "NORMAL MODE" to listOf(FaultId.NORMAL),
        "AUTHORIZATION" to listOf(FaultId.AUTH_REJECT),
        "SIGNING" to listOf(FaultId.SIGN_REJECT, FaultId.DELAY_5S),
        "PROTOCOL" to listOf(FaultId.UNSUPPORTED_CHAIN, FaultId.INVALID_PAYLOAD,
            FaultId.TOO_MANY_PAYLOADS, FaultId.STALE_BLOCKHASH),
        "NETWORK" to listOf(FaultId.RPC_UNAVAILABLE, FaultId.SUBMISSION_FAILURE),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fault Lab", style = MaterialTheme.typography.headlineSmall)
        LabSafetyBanner()
        if (selected.id != FaultId.NORMAL) {
            Card(Modifier.fillMaxWidth().testTag("fault-active-card")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("FAULT ACTIVE", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleLarge)
                    Text(selected.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("INTENTIONAL TEST CONDITION")
                    Text("Expected protocol result: " + (selected.expectedProtocolCode?.let(::protocolErrorText)
                        ?: "Continues through normal flow"))
                    Button(onClick = { onSelect(FaultId.NORMAL) }, modifier = Modifier.testTag("return-to-normal")) {
                        Text("RETURN TO NORMAL")
                    }
                }
            }
        } else {
            Text("NORMAL · no intentional fault selected", style = MaterialTheme.typography.labelLarge)
        }
        LazyColumn(Modifier.weight(1f).testTag("fault-lab"), contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Faults are intentional synthetic conditions, not evidence of a production wallet defect.") }
            groups.forEach { (label, ids) ->
                item { Text(label, style = MaterialTheme.typography.titleMedium) }
                ids.forEach { id ->
                    item(key = id.stableId) {
                        val profile = FaultCatalog.get(id)
                        Card(Modifier.fillMaxWidth().testTag("fault-${id.stableId}")
                            .selectable(selected = selected.id == id, role = Role.RadioButton,
                                onClick = { onSelect(id) })) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(profile.description)
                                Text("Target: " + profile.targetMethods.joinToString { it.name }.ifEmpty { "Normal protocol flow" })
                                Text("Layer: ${profile.hook?.name ?: "NONE"}")
                                Text("Expected protocol result: " + (profile.expectedProtocolCode?.let(::protocolErrorText)
                                    ?: "Continues through normal flow"))
                                Text(id.stableId, style = MaterialTheme.typography.labelSmall)
                                StatusBadge(if (selected.id == id) "SELECTED" else "ACTIVATE",
                                    alert = selected.id == id && id != FaultId.NORMAL)
                            }
                        }
                    }
                }
            }
        }
    }
}
