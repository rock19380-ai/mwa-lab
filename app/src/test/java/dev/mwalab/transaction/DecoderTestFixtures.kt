package dev.mwalab.transaction

import java.math.BigInteger

/** Deterministic hand-framed data and public accounts only; no RPC, wallet keys, or metadata. */
internal object DecoderTestFixtures {
    val systemTransfer = byteArrayOf(2, 0, 0, 0, 0x80.toByte(), 0x96.toByte(), 0x98.toByte(), 0, 0, 0, 0, 0)
    val tokenTransfer = byteArrayOf(3, 0x40, 0x42, 0x0f, 0, 0, 0, 0, 0)
    val tokenTransferChecked = byteArrayOf(12) + tokenTransfer.copyOfRange(1, 9) + byteArrayOf(6)
    val maximumU64 = BigInteger("18446744073709551615")

    fun transaction(
        programId: String,
        data: ByteArray,
        references: List<Int> = listOf(0, 1),
        keyCount: Int = 4,
        signatures: Int = 1,
        readonlySigned: Int = 0,
        version: Int? = null,
        lookups: List<TransactionWireFixtures.Lookup> = emptyList(),
    ): ByteArray {
        val vector = TransactionWireFixtures.build(
            signatures = signatures, readonlySigned = readonlySigned, readonlyUnsigned = 1,
            keys = List(keyCount) { 0x11 * (it + 1) },
            instructions = listOf(TransactionWireFixtures.Instruction(keyCount - 1, references, data)),
            version = version, lookups = lookups,
        )
        val bytes = vector.bytes
        decodeKey(programId).copyInto(bytes, vector.offset("accounts") + (keyCount - 1) * 32)
        return bytes
    }

    fun inspect(program: KnownProgram, data: ByteArray, references: List<Int> = listOf(0, 1), keyCount: Int = 4) =
        TransactionInspector().inspect(transaction(program.programId, data, references, keyCount = keyCount)).instructions!!.single()

    fun decodeMemo(data: ByteArray): InstructionDecoding {
        val bytes = transaction(KnownProgram.MEMO.programId, data, emptyList(), keyCount = 2)
        val structure = SolanaWireTransactionParser().parse(bytes).instructions!!.single()
        return ProgramDecoderRegistry().decode(structure, data)
    }

    fun unsignedData(tag: ByteArray, amount: BigInteger) =
        tag + ByteArray(8) { amount.shiftRight(it * 8).and(BigInteger.valueOf(255)).toByte() }

    private fun decodeKey(value: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var number = BigInteger.ZERO
        value.forEach { number = number * BigInteger.valueOf(58) + BigInteger.valueOf(alphabet.indexOf(it).toLong()) }
        var body = if (number == BigInteger.ZERO) byteArrayOf() else number.toByteArray()
        if (body.size > 1 && body[0] == 0.toByte()) body = body.copyOfRange(1, body.size)
        return (ByteArray(value.takeWhile { it == '1' }.length) + body).also { require(it.size == 32) }
    }
}
