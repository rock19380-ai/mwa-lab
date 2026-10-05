package dev.mwalab.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mwalab.ui.components.DiagnosticValue
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.components.StateNotice
import dev.mwalab.ui.sessions.IdentityUiState
import dev.mwalab.ui.sessions.walletAirdropSummary
import dev.mwalab.ui.sessions.walletBalanceText
import dev.mwalab.wallet.AddressQrCode
import dev.mwalab.wallet.TestWalletUiState
import dev.mwalab.wallet.WalletAirdropState

@Composable
fun LabIdentityScreen(
    identity: IdentityUiState,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    wallet: TestWalletUiState = TestWalletUiState(),
    onRequestAirdrop: () -> Unit = {},
) {
    val address = wallet.address ?: (identity as? IdentityUiState.Ready)?.publicAddress
    LazyColumn(
        Modifier.fillMaxSize().testTag("identity-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Test Wallet", style = MaterialTheme.typography.headlineSmall) }
        item { LabSafetyBanner() }
        item {
            SectionCard("DEVNET TEST WALLET") {
                when {
                    address != null -> {
                        DiagnosticValue("Public address", address)
                        Text(walletBalanceText(wallet.balance))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onCopy(address) }, modifier = Modifier.weight(1f)) {
                                Text("Copy Address")
                            }
                            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                                Text("REFRESH")
                            }
                        }
                    }
                    identity is IdentityUiState.Loading -> Text("Preparing protected Devnet test identity…")
                    else -> StateNotice(
                        "Test wallet unavailable",
                        "The protected Devnet test identity could not be loaded. Protocol signing remains fail-closed.",
                        "Retry",
                        onRetry,
                    )
                }
            }
        }
        if (address != null) {
            item {
                SectionCard("RECEIVE TEST SOL") {
                    Text("DEVNET ONLY", style = MaterialTheme.typography.titleMedium)
                    DiagnosticValue("Address", address)
                    Button(onClick = { onCopy(address) }) { Text("COPY ADDRESS") }
                    AddressQr(payload = address)
                    Text("This QR contains the disposable Devnet test-wallet address.")
                    Text("It is NOT an MWA connection QR.")
                    Text("Use only from a wallet/tool currently set to Solana Devnet. Never send mainnet funds.")
                }
            }
            item {
                SectionCard("REQUEST DEVNET SOL") {
                    Text("Requests 0.5 SOL from the fixed Solana Devnet RPC faucet.")
                    Button(
                        onClick = onRequestAirdrop,
                        enabled = wallet.airdrop !is WalletAirdropState.Requesting &&
                            wallet.airdrop !is WalletAirdropState.Submitted,
                        modifier = Modifier.fillMaxWidth().testTag("wallet-request-devnet-sol"),
                    ) { Text("REQUEST 0.5 DEVNET SOL") }
                    walletAirdropSummary(wallet.airdrop)?.let { Text(it) }
                    if (
                        wallet.airdrop is WalletAirdropState.RateLimited ||
                        wallet.airdrop is WalletAirdropState.RpcUnavailable ||
                        wallet.airdrop is WalletAirdropState.Failed
                    ) {
                        Text("Fallback: copy this address and fund it from another Devnet wallet, Solana CLI, or a supported Devnet faucet.")
                    }
                }
            }
        }
        item {
            SectionCard("PROTECTED LOCALLY") {
                Text("The test identity is protected on this device. MWA Lab never displays a mnemonic, seed, or private key.")
            }
        }
    }
}

@Composable
private fun AddressQr(payload: String) {
    val matrix = remember(payload) { runCatching { AddressQrCode.encode(payload) }.getOrNull() }
    if (matrix == null) {
        Text("QR unavailable for this address. Copy the public address instead.")
        return
    }
    Canvas(
        modifier = Modifier
            .size(240.dp)
            .testTag("receive-address-qr")
            .semantics { contentDescription = "Devnet test wallet address QR" },
    ) {
        drawRect(Color.White)
        val totalModules = matrix.size + AddressQrCode.QUIET_ZONE_MODULES * 2
        val moduleSize = minOf(size.width, size.height) / totalModules.toFloat()
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix.isDark(x, y)) continue
            val left = (x + AddressQrCode.QUIET_ZONE_MODULES) * moduleSize
            val top = (y + AddressQrCode.QUIET_ZONE_MODULES) * moduleSize
            drawRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = Size(moduleSize + 0.25f, moduleSize + 0.25f),
            )
        }
    }
}
