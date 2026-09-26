package dev.mwalab.mwa

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.ui.theme.MWALabTheme

class MobileWalletAdapterActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionHost by lazy {
        MwaSessionHost(
            context = applicationContext,
            onSessionFinished = {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MWALabTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "MWA LAB TEST ENDPOINT",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("SOLANA DEVNET")
                    Text("NO REAL FUNDS")
                    Text("Local Mobile Wallet Adapter session host")
                    Text("Phase 1 signing requests are denied by policy.")
                }
            }
        }

        processAssociationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processAssociationIntent(intent)
    }

    override fun onDestroy() {
        sessionHost.close()
        super.onDestroy()
    }

    private fun processAssociationIntent(intent: Intent?) {
        val result = sessionHost.openAssociation(intent?.data)
        if (result is AssociationOpenResult.Rejected) {
            finish()
        }
    }
}
