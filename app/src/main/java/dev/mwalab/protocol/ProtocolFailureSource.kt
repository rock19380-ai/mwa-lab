package dev.mwalab.protocol

enum class ProtocolFailureSource {
    NONE,
    INJECTED,
    OBSERVED_PROTOCOL,
    SIMULATION,
    RPC_NETWORK,
    LOCAL_PARSER,
    UNKNOWN,
}
