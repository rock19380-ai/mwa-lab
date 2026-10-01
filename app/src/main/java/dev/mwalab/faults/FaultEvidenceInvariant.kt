package dev.mwalab.faults

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolOutcome

/** Validate new writes only; historical Room rows are never reinterpreted. */
object FaultEvidenceInvariant {
    fun requireValid(event: ProtocolEvent) {
        val faultId = event.injectedFaultId
        if (faultId != null) {
            require(FaultCatalog.find(faultId)?.id?.let { it != FaultId.NORMAL } == true) {
                "Injected fault evidence requires a known non-NORMAL ID"
            }
        }
        if (event.failureSource == ProtocolFailureSource.INJECTED) {
            require(event.outcome == ProtocolOutcome.FAILURE && faultId != null) {
                "An injected terminal failure requires an applied fault ID"
            }
        }
    }
}
