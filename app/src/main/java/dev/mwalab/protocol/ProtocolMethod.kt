package dev.mwalab.protocol

enum class ProtocolMethod(val wireName: String) {
    AUTHORIZE("authorize"),
    REAUTHORIZE("reauthorize"),
    DEAUTHORIZE("deauthorize"),
    GET_CAPABILITIES("get_capabilities"),
    SIGN_MESSAGES("sign_messages"),
    SIGN_TRANSACTIONS("sign_transactions"),
    SIGN_AND_SEND_TRANSACTIONS("sign_and_send_transactions"),
}
