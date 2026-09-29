package dev.mwalab.transaction

/** Header-declared privileges only; no RPC ownership, balance, or runtime-lock claim. */
data class AccountSummary(
    val index: Int,
    val publicKey: String,
    val isSigner: Boolean,
    val isWritable: Boolean,
    val isFeePayer: Boolean,
) {
    init {
        require(index in 0 until TransactionInspectionLimits.MAX_ACCOUNTS)
        requireDiagnosticKey(publicKey)
        require(isFeePayer == (index == 0)) { "Fee payer is static account zero" }
        require(!isFeePayer || (isSigner && isWritable)) { "Fee payer must be a writable signer" }
    }
}

/** Unresolved v0 references keep their index and acquire no invented address/privileges. */
data class InstructionAccountReference(val index: Int, val account: AccountSummary?) {
    init {
        require(index in 0 until TransactionInspectionLimits.MAX_ACCOUNTS)
        require(account == null || account.index == index) { "Account reference index mismatch" }
    }
}

data class TransactionMessageHeader(
    val numRequiredSignatures: Int,
    val numReadonlySignedAccounts: Int,
    val numReadonlyUnsignedAccounts: Int,
) {
    init {
        require(numRequiredSignatures in 1..TransactionInspectionLimits.MAX_SIGNATURES)
        require(numReadonlySignedAccounts in 0 until numRequiredSignatures)
        require(numReadonlyUnsignedAccounts in 0 until TransactionInspectionLimits.MAX_ACCOUNTS)
    }

    internal fun isSigner(index: Int): Boolean = index < numRequiredSignatures
    internal fun isWritable(index: Int, staticAccountCount: Int): Boolean =
        if (isSigner(index)) index < numRequiredSignatures - numReadonlySignedAccounts
        else index < staticAccountCount - numReadonlyUnsignedAccounts
}
