package dev.mwalab.protocol

enum class ProtocolMethod(val wireName: String) {
    AUTHORIZE("authorize"),
    DEAUTHORIZE("deauthorize"),
    GET_CAPABILITIES("get_capabilities"),
}
