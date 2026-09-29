package dev.mwalab.capabilities

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CapabilitySnapshotTest {
    @Test
    fun sessionSnapshotMirrorsProfileAndActualWalletConfiguration() {
        val config = MwaCapabilityProfile.createWalletConfig()
        val profile = MwaCapabilityProfile.snapshot()
        val snapshot = MwaCapabilityProfile.snapshotForSession("session-a", 123, config)

        assertEquals("session-a", snapshot.sessionId)
        assertEquals(123L, snapshot.capturedAtEpochMillis)
        assertEquals(CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE, snapshot.source)
        assertEquals(profile.maxTransactionsPerSigningRequest, snapshot.maxTransactionsPerSigningRequest)
        assertEquals(config.maxTransactionsPerSigningRequest, snapshot.maxTransactionsPerSigningRequest)
        assertEquals(profile.maxMessagesPerSigningRequest, snapshot.maxMessagesPerSigningRequest)
        assertEquals(config.maxMessagesPerSigningRequest, snapshot.maxMessagesPerSigningRequest)
        assertEquals(profile.supportedTransactionVersions, snapshot.supportedTransactionVersions)
        assertEquals(config.supportedTransactionVersions.toList(), snapshot.supportedTransactionVersions)
        assertEquals(profile.optionalFeatures, snapshot.optionalFeatures)
        assertEquals(config.optionalFeatures.toList(), snapshot.optionalFeatures)
    }

    @Test
    fun mismatchedRequestLimitsCannotProduceConfiguredSnapshot() {
        val config = MwaCapabilityProfile.createWalletConfig()
        listOf(
            MobileWalletAdapterConfig(
                config.maxTransactionsPerSigningRequest + 1,
                config.maxMessagesPerSigningRequest,
                config.supportedTransactionVersions,
                MwaCapabilityProfile.LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
                config.optionalFeatures,
            ),
            MobileWalletAdapterConfig(
                config.maxTransactionsPerSigningRequest,
                config.maxMessagesPerSigningRequest + 1,
                config.supportedTransactionVersions,
                MwaCapabilityProfile.LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
                config.optionalFeatures,
            ),
        ).forEach { mismatch ->
            assertThrows(IllegalArgumentException::class.java) {
                MwaCapabilityProfile.snapshotForSession("a", 1, mismatch)
            }
        }
    }

    @Test
    fun mismatchedOrNumericTransactionVersionsAreRejectedWithoutStringCoercion() {
        val config = MwaCapabilityProfile.createWalletConfig()
        config.supportedTransactionVersions[0] = "unsupported-version"
        assertThrows(IllegalArgumentException::class.java) {
            MwaCapabilityProfile.snapshotForSession("a", 1, config)
        }
        val numeric = MobileWalletAdapterConfig(
            config.maxTransactionsPerSigningRequest,
            config.maxMessagesPerSigningRequest,
            arrayOf<Any>(0),
            MwaCapabilityProfile.LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
            config.optionalFeatures,
        )
        assertThrows(IllegalArgumentException::class.java) {
            MwaCapabilityProfile.snapshotForSession("a", 1, numeric)
        }
    }

    @Test
    fun mismatchedOptionalFeaturesAreRejected() {
        val config = MwaCapabilityProfile.createWalletConfig()
        config.optionalFeatures[0] = "solana:unsupportedFeature"
        assertThrows(IllegalArgumentException::class.java) {
            MwaCapabilityProfile.snapshotForSession("a", 1, config)
        }
    }

    @Test
    fun snapshotOwnsCopiesAndExposesUnmodifiableCollections() {
        val config = MwaCapabilityProfile.createWalletConfig()
        val snapshot = MwaCapabilityProfile.snapshotForSession("a", 1, config)
        val versions = snapshot.supportedTransactionVersions.toList()
        val features = snapshot.optionalFeatures.toList()
        config.supportedTransactionVersions[0] = "changed-version"
        config.optionalFeatures[0] = "solana:changedFeature"
        assertEquals(versions, snapshot.supportedTransactionVersions)
        assertEquals(features, snapshot.optionalFeatures)

        val mutableVersions = versions.toMutableList()
        val mutableFeatures = features.toMutableList()
        val owned = domain(versions = mutableVersions, features = mutableFeatures)
        mutableVersions.clear()
        mutableFeatures.clear()
        assertEquals(versions, owned.supportedTransactionVersions)
        assertEquals(features, owned.optionalFeatures)
        assertThrows(UnsupportedOperationException::class.java) {
            (owned.supportedTransactionVersions as MutableList<String>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (owned.optionalFeatures as MutableList<String>).clear()
        }
    }

    @Test
    fun invalidSessionTimestampAndLimitsAreRejected() {
        listOf("", " ", "unknown").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { domain(id = id) }
        }
        assertThrows(IllegalArgumentException::class.java) { domain(timestamp = -1) }
        assertThrows(IllegalArgumentException::class.java) { domain(transactions = -1) }
        assertThrows(IllegalArgumentException::class.java) { domain(messages = -1) }
    }

    @Test
    fun invalidDuplicateAndUnboundedCollectionMetadataIsRejected() {
        val profile = MwaCapabilityProfile.snapshot()
        val version = profile.supportedTransactionVersions.single() as String
        val feature = profile.optionalFeatures.single()
        listOf(emptyList(), listOf(version, version), listOf("not a version"),
            (0..CapabilitySnapshot.MAX_COLLECTION_SIZE).map { "version$it" },
        ).forEach { values ->
            assertThrows(IllegalArgumentException::class.java) { domain(versions = values) }
        }
        listOf(listOf(feature, feature), listOf("raw\nmetadata"),
            listOf("no-feature-namespace"), listOf("a:" + "x".repeat(129)),
        ).forEach { values ->
            assertThrows(IllegalArgumentException::class.java) { domain(features = values) }
        }
    }

    @Test
    fun snapshotsAreSessionScopedWithStableValueEquality() {
        val first = MwaCapabilityProfile.snapshotForSession("a", 1, MwaCapabilityProfile.createWalletConfig())
        val same = MwaCapabilityProfile.snapshotForSession("a", 1, MwaCapabilityProfile.createWalletConfig())
        val next = MwaCapabilityProfile.snapshotForSession("b", 2, MwaCapabilityProfile.createWalletConfig())
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, next)
    }

    private fun domain(
        id: String = "a",
        timestamp: Long = 1,
        transactions: Int = MwaCapabilityProfile.snapshot().maxTransactionsPerSigningRequest,
        messages: Int = MwaCapabilityProfile.snapshot().maxMessagesPerSigningRequest,
        versions: List<String> = MwaCapabilityProfile.snapshot().supportedTransactionVersions.map { it as String },
        features: List<String> = MwaCapabilityProfile.snapshot().optionalFeatures,
    ) = CapabilitySnapshot(
        id, timestamp, CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE,
        transactions, messages, versions, features,
    )
}
