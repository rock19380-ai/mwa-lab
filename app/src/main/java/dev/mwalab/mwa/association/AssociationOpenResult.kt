package dev.mwalab.mwa.association

sealed interface AssociationOpenResult {
    data class Accepted(
        val localPort: Int,
    ) : AssociationOpenResult

    data class Rejected(
        val reason: Reason,
    ) : AssociationOpenResult

    enum class Reason {
        MISSING_URI,
        UNSUPPORTED_SCHEME,
        INVALID_ASSOCIATION,
        NON_LOCAL_ASSOCIATION,
        SCENARIO_CREATION_FAILED,
        SCENARIO_START_FAILED,
    }
}
