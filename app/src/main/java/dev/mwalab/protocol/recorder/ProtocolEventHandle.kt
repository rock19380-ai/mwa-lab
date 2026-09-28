package dev.mwalab.protocol.recorder

import dev.mwalab.protocol.EventId
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.session.SessionId

/**
 * Immutable request-start binding. It is diagnostic state only and grants no
 * authorization, approval, signing, or response authority.
 */
class ProtocolEventHandle internal constructor(
    val sessionId: SessionId,
    val eventId: EventId,
    val sequence: Long,
    val method: ProtocolMethod,
    val startedAtEpochMillis: Long,
    val requestSummary: Map<String, String>,
)
