package dev.mwalab.capabilities

import dev.mwalab.session.SessionId
import java.util.Collections

/** An immutable copy of the capability profile configured for one Lab session. */
class CapabilitySnapshot(
    val sessionId: SessionId,
    val capturedAtEpochMillis: Long,
    val source: CapabilitySnapshotSource,
    val maxTransactionsPerSigningRequest: Int,
    val maxMessagesPerSigningRequest: Int,
    supportedTransactionVersions: List<String>,
    optionalFeatures: List<String>,
) {
    val supportedTransactionVersions: List<String> =
        Collections.unmodifiableList(ArrayList(supportedTransactionVersions))
    val optionalFeatures: List<String> =
        Collections.unmodifiableList(ArrayList(optionalFeatures))

    init {
        require(sessionId.isNotBlank() && sessionId != "unknown") {
            "A capability snapshot needs an assigned session"
        }
        require(capturedAtEpochMillis >= 0) { "Capture must be an epoch timestamp" }
        require(maxTransactionsPerSigningRequest >= 0 && maxMessagesPerSigningRequest >= 0) {
            "Configured request limits must be nonnegative"
        }
        require(this.supportedTransactionVersions.isNotEmpty()) {
            "Configured transaction versions must not be empty"
        }
        validateList(this.supportedTransactionVersions, VERSION_LABEL)
        validateList(this.optionalFeatures, FEATURE_ID)
    }

    override fun equals(other: Any?): Boolean = other is CapabilitySnapshot &&
        sessionId == other.sessionId &&
        capturedAtEpochMillis == other.capturedAtEpochMillis &&
        source == other.source &&
        maxTransactionsPerSigningRequest == other.maxTransactionsPerSigningRequest &&
        maxMessagesPerSigningRequest == other.maxMessagesPerSigningRequest &&
        supportedTransactionVersions == other.supportedTransactionVersions &&
        optionalFeatures == other.optionalFeatures

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + capturedAtEpochMillis.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + maxTransactionsPerSigningRequest
        result = 31 * result + maxMessagesPerSigningRequest
        result = 31 * result + supportedTransactionVersions.hashCode()
        return 31 * result + optionalFeatures.hashCode()
    }

    companion object {
        // Representation bounds, not a second authority for configured capabilities.
        internal const val MAX_COLLECTION_SIZE = 64
        private val VERSION_LABEL = Regex("[a-z][a-z0-9._-]{0,31}")
        private val FEATURE_ID = Regex("[a-z][a-z0-9+.-]*:[A-Za-z0-9._/-]+")

        private fun validateList(values: List<String>, pattern: Regex) {
            require(values.size <= MAX_COLLECTION_SIZE && values.distinct().size == values.size) {
                "Capability values must be bounded and unique"
            }
            require(values.all { it.length <= 128 && pattern.matches(it) }) {
                "Invalid capability metadata representation"
            }
        }
    }
}
