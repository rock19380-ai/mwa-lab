package dev.mwalab.transaction

/** Lookup descriptors only. These table indexes are not resolved public account addresses. */
class AddressTableLookupSummary(
    val index: Int,
    val tableAccount: String,
    writableIndexes: List<Int>,
    readonlyIndexes: List<Int>,
) {
    val writableIndexes = immutableDiagnosticList(writableIndexes, TransactionInspectionLimits.MAX_ACCOUNTS)
    val readonlyIndexes = immutableDiagnosticList(readonlyIndexes, TransactionInspectionLimits.MAX_ACCOUNTS)
    val referencedAccountCount: Int get() = writableIndexes.size + readonlyIndexes.size

    init {
        require(index in 0 until TransactionInspectionLimits.MAX_ADDRESS_TABLE_LOOKUPS)
        requireDiagnosticKey(tableAccount)
        require(referencedAccountCount in 1..TransactionInspectionLimits.MAX_ACCOUNTS)
        require((this.writableIndexes + this.readonlyIndexes).all { it in 0..255 })
    }

    override fun equals(other: Any?): Boolean = other is AddressTableLookupSummary &&
        index == other.index && tableAccount == other.tableAccount &&
        writableIndexes == other.writableIndexes && readonlyIndexes == other.readonlyIndexes
    override fun hashCode(): Int = listOf(index, tableAccount, writableIndexes, readonlyIndexes).hashCode()
}
