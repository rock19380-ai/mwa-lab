package dev.mwalab.faults

import dev.mwalab.protocol.ProtocolMethod

data class FaultProfile(
    val id: FaultId,
    val displayName: String,
    val description: String,
    val targetMethods: Set<ProtocolMethod>,
    val hook: FaultHook?,
    val expectedProtocolCode: Int?,
) {
    init {
        require(displayName.isNotBlank() && description.isNotBlank())
        if (id == FaultId.NORMAL) {
            require(targetMethods.isEmpty() && hook == null && expectedProtocolCode == null)
        } else {
            require(targetMethods.isNotEmpty() && hook != null)
        }
    }
}
