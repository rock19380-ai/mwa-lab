package dev.mwalab.report

import com.solana.mobilewalletadapter.common.ProtocolContract

/** Display-only names for the pinned walletlib codes; unknown codes stay unknown. */
object ReportProtocolErrorName {
    fun forCode(code: Int?): String? = when (code) {
        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> "ERROR_AUTHORIZATION_FAILED"
        ProtocolContract.ERROR_INVALID_PAYLOADS -> "ERROR_INVALID_PAYLOADS"
        ProtocolContract.ERROR_NOT_SIGNED -> "ERROR_NOT_SIGNED"
        ProtocolContract.ERROR_NOT_SUBMITTED -> "ERROR_NOT_SUBMITTED"
        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> "ERROR_TOO_MANY_PAYLOADS"
        ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED -> "ERROR_CLUSTER_NOT_SUPPORTED"
        else -> null
    }
}
