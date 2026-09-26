package dev.mwalab.democlient

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import java.util.concurrent.TimeUnit

enum class DemoStepName {
    CONNECTION,
    AUTHORIZE,
    CAPABILITIES,
    DEAUTHORIZE,
}

data class DemoStepEvidence(
    val step: DemoStepName,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val passed: Boolean,
    val summary: String,
) {
    val durationMillis: Long
        get() = (completedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0)
}

data class DemoRunResult(
    val walletPackage: String,
    val accountBase58: String,
    val maxTransactionsPerSigningRequest: Int,
    val maxMessagesPerSigningRequest: Int,
    val supportedTransactionVersions: List<Any>,
    val optionalFeatures: List<String>,
    val steps: List<DemoStepEvidence>,
)

class DemoClientRunner(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun runCanonical(): DemoRunResult {
        val evidence = mutableListOf<DemoStepEvidence>()
        val localAssociation =
            LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

        try {
            val connectionStarted = System.currentTimeMillis()
            val implicitIntent = LocalAssociationIntentCreator.createAssociationIntent(
                null,
                localAssociation.port,
                localAssociation.session,
            )

            val resolvers = appContext.packageManager.queryIntentActivities(
                implicitIntent,
                0,
            )
            val walletResolver = resolvers.firstOrNull {
                it.activityInfo.packageName == WALLET_PACKAGE
            } ?: error("MWA Lab wallet endpoint is not discoverable")

            check(walletResolver.activityInfo.packageName != appContext.packageName) {
                "Demo client and wallet endpoint must be different Android packages"
            }

            val walletIntent = Intent(implicitIntent)
                .setPackage(WALLET_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            appContext.startActivity(walletIntent)

            val client = localAssociation.start()
                .get(30, TimeUnit.SECONDS)

            evidence += DemoStepEvidence(
                step = DemoStepName.CONNECTION,
                startedAtEpochMillis = connectionStarted,
                completedAtEpochMillis = System.currentTimeMillis(),
                passed = true,
                summary = "real_local_association",
            )

            val authorizeStarted = System.currentTimeMillis()
            val authorization = client.authorize(
                Uri.parse("https://phase1-demo-client.invalid"),
                Uri.parse("icon.png"),
                CLIENT_IDENTITY_NAME,
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)

            check(authorization.accounts.size == 1) {
                "Expected exactly one Phase 1 Lab account"
            }

            val accountBase58 = Base58.encodeToString(
                authorization.accounts.single().publicKey,
            )

            evidence += DemoStepEvidence(
                step = DemoStepName.AUTHORIZE,
                startedAtEpochMillis = authorizeStarted,
                completedAtEpochMillis = System.currentTimeMillis(),
                passed = true,
                summary = "devnet_account_received",
            )

            val capabilityStarted = System.currentTimeMillis()
            val capabilities = client.getCapabilities()
                .get(10, TimeUnit.SECONDS)

            evidence += DemoStepEvidence(
                step = DemoStepName.CAPABILITIES,
                startedAtEpochMillis = capabilityStarted,
                completedAtEpochMillis = System.currentTimeMillis(),
                passed = true,
                summary = "pinned_walletlib_capabilities_received",
            )

            val deauthorizeStarted = System.currentTimeMillis()
            client.deauthorize(authorization.authToken)
                .get(10, TimeUnit.SECONDS)

            evidence += DemoStepEvidence(
                step = DemoStepName.DEAUTHORIZE,
                startedAtEpochMillis = deauthorizeStarted,
                completedAtEpochMillis = System.currentTimeMillis(),
                passed = true,
                summary = "authorization_revoked",
            )

            return DemoRunResult(
                walletPackage = walletResolver.activityInfo.packageName,
                accountBase58 = accountBase58,
                maxTransactionsPerSigningRequest =
                    capabilities.maxTransactionsPerSigningRequest,
                maxMessagesPerSigningRequest =
                    capabilities.maxMessagesPerSigningRequest,
                supportedTransactionVersions =
                    capabilities.supportedTransactionVersions.toList(),
                optionalFeatures =
                    capabilities.supportedOptionalFeatures.toList(),
                steps = evidence.toList(),
            )
        } finally {
            runCatching {
                localAssociation.close().get(10, TimeUnit.SECONDS)
            }
        }
    }

    companion object {
        const val CLIENT_IDENTITY_NAME = "MWA Lab Demo Client — FOR TESTING ONLY"
        private const val WALLET_PACKAGE = "dev.mwalab"
    }
}
