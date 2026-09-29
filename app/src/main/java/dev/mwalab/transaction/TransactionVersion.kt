package dev.mwalab.transaction

sealed interface TransactionVersion {
    data object LEGACY : TransactionVersion
    data object V0 : TransactionVersion
    data class VERSIONED_UNSUPPORTED(val number: Int) : TransactionVersion {
        init { require(number in 1..127) { "Unsupported version must fit the wire prefix" } }
    }
    data object UNKNOWN : TransactionVersion
}
