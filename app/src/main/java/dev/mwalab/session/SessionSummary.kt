package dev.mwalab.session

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolOutcome
import java.util.Collections

enum class SessionStatus {
    ACTIVE,
    PASS,
    FAIL,
    CANCELLED,
    // A closed session with no observed methods is not a proven protocol success.
    UNKNOWN,
}

/**
 * Derived view of a session and its terminal events, never separately persisted
 * status. The recorder must settle begun events on close before publishing a
 * final aggregate; incomplete history must not be passed off as complete.
 */
class SessionSummary(
    val session: MwaSession,
    events: List<ProtocolEvent>,
) {
    val events: List<ProtocolEvent> = Collections.unmodifiableList(events.sortedBy { it.sequence })

    init {
        require(this.events.all { it.sessionId == session.id }) { "Events must belong to this session" }
        require(this.events.all { it.sequence > 0 && it.eventId.isNotBlank() && it.eventId != "unknown:0" }) {
            "Events need assigned identities and positive sequence numbers"
        }
        require(this.events.map { it.sequence }.distinct().size == this.events.size) {
            "Sequence must be unique within a session"
        }
        require(this.events.map { it.eventId }.distinct().size == this.events.size) {
            "Event identities must be unique"
        }
    }

    val eventCount: Int get() = events.size
    val durationMillis: Long? get() = session.durationMillis

    val status: SessionStatus
        get() = when {
            session.completedAtEpochMillis == null -> SessionStatus.ACTIVE
            events.any { it.outcome == ProtocolOutcome.FAILURE } -> SessionStatus.FAIL
            session.closeReason == SessionCloseReason.SCENARIO_ERROR ||
                session.closeReason == SessionCloseReason.START_FAILED -> SessionStatus.FAIL
            events.any { it.outcome == ProtocolOutcome.CANCELLED } -> SessionStatus.CANCELLED
            session.closeReason != SessionCloseReason.SERVING_COMPLETE &&
                session.closeReason != SessionCloseReason.SCENARIO_COMPLETE -> SessionStatus.CANCELLED
            events.isEmpty() -> SessionStatus.UNKNOWN
            else -> SessionStatus.PASS
        }
}
