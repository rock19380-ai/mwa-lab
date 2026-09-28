package dev.mwalab.session

/** Observed lifecycle cause, independent of a method's protocol error/source. */
enum class SessionCloseReason {
    SERVING_COMPLETE,
    SCENARIO_COMPLETE,
    SCENARIO_ERROR,
    TEARDOWN_COMPLETE,
    LOW_POWER_NO_CONNECTION,
    HOST_CLOSED,
    // openAssociation closes the old scenario even if the new intent is invalid.
    REPLACED_BY_ASSOCIATION_ATTEMPT,
    START_FAILED,
}
