package dev.mwalab.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mwalab.faults.FaultId
import dev.mwalab.faults.FaultProfile
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard
import dev.mwalab.ui.sessions.HomeUiState
import dev.mwalab.ui.sessions.IdentityUiState
import dev.mwalab.wallet.WalletBalanceState
import dev.mwalab.wallet.formatLamportsAsSol
import dev.mwalab.wallet.walletUnavailableReasonText

@Composable
fun OnboardingScreen(
    activeFault: FaultProfile,
    home: HomeUiState,
    onEnter: () -> Unit,
) {
    val address = home.wallet.address ?: (home.identity as? IdentityUiState.Ready)?.publicAddress
    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("MWA LAB", style = MaterialTheme.typography.displaySmall) }
        item { Text("MWA Protocol Debugger", style = MaterialTheme.typography.titleLarge) }
        item { LabSafetyBanner() }
        item {
            Text(
                if (activeFault.id == FaultId.NORMAL) "Current mode: NORMAL" else
                    "FAULT ACTIVE · ${activeFault.displayName} · INTENTIONAL TEST CONDITION",
            )
        }
        item {
            SectionCard("YOUR TEST WALLET") {
                Text(address ?: "Preparing protected Devnet test address…")
                Text(
                    when (val balance = home.wallet.balance) {
                        WalletBalanceState.Loading -> "Balance: loading…"
                        is WalletBalanceState.Available -> "${formatLamportsAsSol(balance.lamports)} SOL · Devnet"
                        is WalletBalanceState.Unavailable -> "Balance unavailable · ${walletUnavailableReasonText(balance.reason)}"
                    },
                )
                Text("Disposable Devnet identity. Never use real funds.")
            }
        }
        item {
            SectionCard("TEST ON THIS PHONE") {
                Text("1. Open your MWA-enabled Solana Android dApp.")
                Text("2. Tap Connect Wallet.")
                Text("3. Choose MWA Lab if Android asks.")
                Text("4. Approve the Devnet test connection in MWA Lab.")
            }
        }
        item {
            SectionCard("REMOTE TESTING") {
                Text("Remote controls are shown only after Remote MWA passes its release gate in this build.")
            }
        }
        item {
            Button(onClick = onEnter, modifier = Modifier.fillMaxWidth()) { Text("ENTER LAB") }
        }
    }
}
