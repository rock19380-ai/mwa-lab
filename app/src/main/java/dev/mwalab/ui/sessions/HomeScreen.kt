package dev.mwalab.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.faults.FaultId
import dev.mwalab.faults.FaultProfile
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.components.StateNotice
import dev.mwalab.ui.components.StatusBadge
import dev.mwalab.wallet.WalletAirdropState
import dev.mwalab.wallet.WalletBalanceState
import dev.mwalab.wallet.formatLamportsAsSol
import dev.mwalab.wallet.walletUnavailableReasonText

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSessions: () -> Unit,
    onSession: (String) -> Unit,
    onRetry: () -> Unit,
    activeFault: FaultProfile? = null,
    onFaultLab: () -> Unit = {},
    onIdentity: () -> Unit = {},
    onCopyAddress: (String) -> Unit = {},
    onRefreshWallet: () -> Unit = {},
    onRequestAirdrop: () -> Unit = {},
) {
    var showHowToConnect by remember { mutableStateOf(false) }
    val address = state.wallet.address ?: (state.identity as? IdentityUiState.Ready)?.publicAddress

    if (showHowToConnect) {
        HowToConnectDialog(onDismiss = { showHowToConnect = false })
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("home-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("MWA LAB", style = MaterialTheme.typography.displaySmall)
                Text("MWA Protocol Debugger", style = MaterialTheme.typography.titleMedium)
                Text("Mobile Wallet Adapter protocol debugger and deterministic failure simulator")
            }
        }
        item { LabSafetyBanner(Modifier.testTag("home-safety-banner")) }
        item {
            SectionCard("READY FOR DAPP CONNECTIONS") {
                StatusBadge("DEVNET ONLY", Modifier.testTag("home-connection-devnet-badge"))
                Text("TEST A DAPP ON THIS PHONE", style = MaterialTheme.typography.titleMedium)
                Text("1. Open your Solana Android dApp")
                Text("2. Tap Connect Wallet")
                Text("3. Choose MWA Lab")
                Text("MWA Lab opens automatically for a same-device association.")
                Button(
                    onClick = { showHowToConnect = true },
                    modifier = Modifier.fillMaxWidth().testTag("how-to-connect"),
                ) { Text("HOW TO CONNECT") }
            }
        }
        item {
            SectionCard("TEST WALLET") {
                when {
                    address == null && state.identity is IdentityUiState.Loading ->
                        Text("Preparing protected Devnet test wallet…")
                    address == null -> Text("Test wallet unavailable. Protocol signing remains fail-closed.")
                    else -> {
                        Text(shortAddress(address), style = MaterialTheme.typography.titleMedium)
                        Text(walletBalanceText(state.wallet.balance))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onCopyAddress(address) },
                                modifier = Modifier.weight(1f),
                            ) { Text("COPY ADDRESS") }
                            OutlinedButton(
                                onClick = onRefreshWallet,
                                modifier = Modifier.weight(1f),
                            ) { Text("REFRESH") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onIdentity,
                                modifier = Modifier.weight(1f).testTag("receive-test-sol"),
                            ) { Text("RECEIVE TEST SOL") }
                            Button(
                                onClick = onRequestAirdrop,
                                enabled = state.wallet.airdrop !is WalletAirdropState.Requesting &&
                                    state.wallet.airdrop !is WalletAirdropState.Submitted,
                                modifier = Modifier.weight(1f).testTag("request-devnet-sol"),
                            ) { Text("REQUEST DEVNET SOL") }
                        }
                        walletAirdropSummary(state.wallet.airdrop)?.let { Text(it) }
                        TextButton(onClick = onIdentity) { Text("OPEN TEST WALLET") }
                    }
                }
            }
        }
        item {
            SectionCard("FAULT MODE") {
                val profile = activeFault
                if (profile == null || profile.id == FaultId.NORMAL) {
                    StatusBadge("NORMAL")
                    Text("No intentional fault selected.")
                } else {
                    StatusBadge("FAULT ACTIVE", alert = true)
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("FAULT ACTIVE · INTENTIONAL TEST CONDITION")
                }
                OutlinedButton(onClick = onFaultLab) { Text("OPEN FAULT LAB") }
            }
        }
        item {
            when (val history = state.history) {
                SessionsUiState.Loading -> StateNotice("RECENT SESSION", "Loading persisted history…")
                SessionsUiState.Empty -> StateNotice(
                    "NO SESSIONS YET",
                    "Connect the MWA Lab Demo Client or a compatible MWA dApp to record a protocol session.",
                )
                SessionsUiState.Error -> StateNotice(
                    "SESSION HISTORY UNAVAILABLE",
                    "Persisted history could not be loaded.",
                    "Retry",
                    onRetry,
                )
                is SessionsUiState.Ready -> {
                    val summary = history.sessions.firstOrNull()
                    if (summary == null) {
                        StateNotice("NO SESSIONS YET", "Connect a compatible MWA dApp.")
                    } else {
                        SectionCard("RECENT SESSION") {
                            Text(summary.session.dappIdentityName ?: "Unknown dApp", style = MaterialTheme.typography.titleMedium)
                            StatusBadge(summary.status.name, alert = summary.status.name == "FAIL")
                            Text("${summary.eventCount} events · ${summary.durationMillis?.let { "$it ms" } ?: "Recorded open"}")
                            failureText(summary)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = { onSession(summary.session.id) }) { Text("OPEN LAST SESSION") }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onSessions, modifier = Modifier.fillMaxWidth()) { Text("VIEW SESSIONS") }
        }
    }
}

@Composable
private fun HowToConnectDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HOW TO CONNECT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SAME-DEVICE MWA", style = MaterialTheme.typography.titleMedium)
                Text("1. Install MWA Lab on the Android device.")
                Text("2. Open your MWA-enabled Android dApp.")
                Text("3. Tap Connect Wallet.")
                Text("4. Choose MWA Lab if Android asks.")
                Text("5. Approve the Devnet test connection.")
                Text("TROUBLESHOOTING", style = MaterialTheme.typography.titleMedium)
                Text("• If MWA Lab is not listed, confirm the dApp actually uses Mobile Wallet Adapter.")
                Text("• Only Solana Devnet is supported.")
                Text("• Retry after a stale or canceled association.")
                Text("• Open Recent Session for structured protocol evidence after a session begins.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
    )
}

internal fun walletBalanceText(balance: WalletBalanceState): String = when (balance) {
    WalletBalanceState.Loading -> "Balance: loading…"
    is WalletBalanceState.Available -> "${formatLamportsAsSol(balance.lamports)} SOL · Devnet"
    is WalletBalanceState.Unavailable -> "Balance unavailable · ${walletUnavailableReasonText(balance.reason)}"
}

internal fun walletAirdropSummary(state: WalletAirdropState): String? = when (state) {
    WalletAirdropState.Idle -> null
    WalletAirdropState.Requesting -> "Requesting Devnet SOL…"
    WalletAirdropState.Submitted -> "Airdrop submitted · waiting for confirmed commitment…"
    WalletAirdropState.Confirmed -> "Airdrop confirmed · balance refreshed."
    WalletAirdropState.RateLimited -> "Airdrop rate-limited. Copy the address and fund it from another Devnet wallet, Solana CLI, or a supported Devnet faucet."
    is WalletAirdropState.RpcUnavailable -> "Airdrop unavailable (${walletUnavailableReasonText(state.reason)}). The debugger remains usable; fund the copied address from another Devnet source."
    is WalletAirdropState.Failed -> "Airdrop failed${state.rpcCode?.let { " · RPC $it" } ?: ""}. No success was assumed."
    WalletAirdropState.UnknownConfirmation -> "Airdrop was submitted but confirmation is unknown. Refresh balance before retrying."
}

private fun shortAddress(address: String): String =
    if (address.length > 14) "${address.take(7)}…${address.takeLast(7)}" else address
