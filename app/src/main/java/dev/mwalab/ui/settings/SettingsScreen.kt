package dev.mwalab.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mwalab.rpc.SolanaDevnetRpcGateway
import dev.mwalab.ui.components.DiagnosticValue
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.components.StatusBadge

data class SettingsSafetyInfo(
    val network: String = "Solana Devnet",
    val rpc: String = SolanaDevnetRpcGateway.DEVNET_RPC_URL,
    val report: String = "Sanitized Report v1",
    val mainnet: String = "Unavailable",
)

@Composable
fun SettingsScreen(mode: ThemeMode, onMode: (ThemeMode) -> Unit, version: String) {
    val info = SettingsSafetyInfo()
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item { LabSafetyBanner() }
        item { SectionCard("Appearance") {
            ThemeMode.entries.forEach { option ->
                androidx.compose.material3.Surface(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("theme-mode-${option.name}")
                    .selectable(selected = mode == option,
                        role = Role.RadioButton, onClick = { onMode(option) })) {
                    androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        StatusBadge(if (mode == option) "SELECTED" else "CHOOSE")
                        Text(option.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } }
        item { SectionCard("Fixed security configuration") {
            DiagnosticValue("Network", info.network)
            DiagnosticValue("RPC · fixed for this release", info.rpc)
            DiagnosticValue("Diagnostic reports · fixed", info.report)
            DiagnosticValue("Mainnet", info.mainnet)
        } }
        item { SectionCard("Build") { Text(version) } }
    }
}
