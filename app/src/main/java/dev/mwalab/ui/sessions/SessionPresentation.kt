package dev.mwalab.ui.sessions

import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.session.SessionStatus
import dev.mwalab.session.SessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun timestampText(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMillis))

fun sessionStatusText(status: SessionStatus): String = when (status) {
    SessionStatus.ACTIVE -> "ACTIVE · recorded open"
    SessionStatus.PASS -> "PASS"
    SessionStatus.FAIL -> "FAIL"
    SessionStatus.CANCELLED -> "CANCELLED / INTERRUPTED"
    SessionStatus.UNKNOWN -> "UNKNOWN · no observed methods"
}

fun protocolErrorText(code: Int?): String {
    if (code == null) return "No protocol error recorded"
    val name = when (code) {
        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> "ERROR_AUTHORIZATION_FAILED"
        ProtocolContract.ERROR_INVALID_PAYLOADS -> "ERROR_INVALID_PAYLOADS"
        ProtocolContract.ERROR_NOT_SIGNED -> "ERROR_NOT_SIGNED"
        ProtocolContract.ERROR_NOT_SUBMITTED -> "ERROR_NOT_SUBMITTED"
        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> "ERROR_TOO_MANY_PAYLOADS"
        ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED -> "ERROR_CLUSTER_NOT_SUPPORTED"
        else -> null
    }
    return if (name == null) "Protocol error: $code (Unknown protocol error)"
        else "Protocol error: $name ($code)"
}

fun failureText(summary: SessionSummary): String? = summary.events
    .firstOrNull { it.outcome == ProtocolOutcome.FAILURE }
    ?.let { "${it.method.name} · ${protocolErrorText(it.protocolErrorCode)} · ${it.failureSource.name}" }

/** An applied condition is independent of the terminal failure source. */
fun injectedConditionText(event: ProtocolEvent): String? = event.injectedFaultId?.let { id ->
    val name = FaultCatalog.find(id)?.displayName
    if (name == null) "Injected condition: $id" else "Injected condition: $name ($id)"
}

fun sessionHasInjectedCondition(summary: SessionSummary): Boolean =
    summary.events.any { it.injectedFaultId != null }
