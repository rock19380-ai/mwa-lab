package dev.mwalab.transaction

import java.io.ByteArrayOutputStream

/** Fixed public bytes, unsigned signatures, and deterministic framing; no RPC or generated keys. */
internal object TransactionWireFixtures {
    val legacyKeys = listOf(
        "29d2S7vB453rNYFdR5Ycwt7y9haRT5fwVwL9zTmBhfV2",
        "3JF3sEqM796hk5WFqA6EtmEwJQ9quALszsfJyvXNQKy3",
        "4Ss5JMkXAD9Z7cktFEdrqeMuT6jGMF1pVozTyPHZ6zT4",
        "5bV6jUfhDHCQVA1WfKBUnXUsboJgoKgkzkKcxr3joew5",
        "6k78AbasGMFFrhG95Pj6jQbqkVt7FQMhVgemxJovWKR6",
    )
    const val blockhash = "7tj9biW3KRJ7EEWmVUGigHiouCTXhV2dzcyvwma7Cyu7"
    const val legacyFingerprint = "eeb2289d389bcf7c815f2c96c4865281e6e2d67e319f0e2cae843314b80decc7"
    const val dataFingerprint = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"

    data class Instruction(val program: Int, val accounts: List<Int>, val data: ByteArray = byteArrayOf())
    data class Lookup(val keyByte: Int, val writable: List<Int>, val readonly: List<Int>)
    data class Vector(val bytes: ByteArray, val offsets: Map<String, Int>) {
        fun offset(name: String) = offsets.getValue(name)
        fun replace(name: String, value: Int): ByteArray = bytes.copyOf().also { it[offset(name)] = value.toByte() }
        fun replaceLength(name: String, value: Int): ByteArray =
            bytes.copyOfRange(0, offset(name)) + shortVec(value) + bytes.copyOfRange(offset(name) + 1, bytes.size)
    }

    fun legacy() = build(
        signatures = 2, required = 2, readonlySigned = 1, readonlyUnsigned = 1,
        keys = listOf(0x11, 0x22, 0x33, 0x44, 0x55),
        instructions = listOf(Instruction(4, listOf(0, 2, 1, 3), byteArrayOf(1, 2, 3)),
            Instruction(3, listOf(2, 2))),
    )

    fun v0() = build(version = 0, keys = listOf(0x11, 0x55), readonlyUnsigned = 1,
        instructions = listOf(Instruction(1, listOf(0, 2, 3, 4), byteArrayOf(0xaa.toByte(), 0xbb.toByte()))),
        lookups = listOf(Lookup(0x66, listOf(7), listOf(9)), Lookup(0x77, listOf(2), emptyList())))

    fun build(
        signatures: Int = 1,
        required: Int = signatures,
        readonlySigned: Int = 0,
        readonlyUnsigned: Int = 0,
        keys: List<Int> = listOf(0x11, 0x55),
        instructions: List<Instruction> = emptyList(),
        version: Int? = null,
        lookups: List<Lookup> = emptyList(),
    ): Vector {
        val out = ByteArrayOutputStream()
        val offsets = mutableMapOf<String, Int>()
        fun mark(name: String) { offsets[name] = out.size() }
        fun writeLength(name: String, value: Int) { mark(name); out.write(shortVec(value)) }
        writeLength("signatureCount", signatures)
        mark("signatures")
        repeat(signatures) { signatureIndex -> out.write(ByteArray(64) { (0xa1 + signatureIndex).toByte() }) }
        mark("message")
        if (version != null) out.write(0x80 or version)
        mark("header"); out.write(required); out.write(readonlySigned); out.write(readonlyUnsigned)
        writeLength("accountCount", keys.size)
        mark("accounts")
        keys.forEach { out.write(ByteArray(32) { _ -> it.toByte() }) }
        mark("blockhash"); out.write(ByteArray(32) { 0x66 })
        writeLength("instructionCount", instructions.size)
        instructions.forEachIndexed { index, instruction ->
            mark("program$index"); out.write(instruction.program)
            writeLength("referenceCount$index", instruction.accounts.size)
            mark("references$index"); instruction.accounts.forEach(out::write)
            writeLength("dataLength$index", instruction.data.size)
            mark("data$index"); out.write(instruction.data)
        }
        if (version != null) {
            writeLength("lookupCount", lookups.size)
            lookups.forEachIndexed { index, lookup ->
                mark("lookup$index"); out.write(ByteArray(32) { lookup.keyByte.toByte() })
                writeLength("lookupWritableCount$index", lookup.writable.size)
                mark("lookupWritable$index"); lookup.writable.forEach(out::write)
                writeLength("lookupReadonlyCount$index", lookup.readonly.size)
                mark("lookupReadonly$index"); lookup.readonly.forEach(out::write)
            }
        }
        return Vector(out.toByteArray(), offsets)
    }

    fun shortVec(value: Int): ByteArray {
        require(value in 0..65535)
        val out = ByteArrayOutputStream()
        var remaining = value
        do {
            val part = remaining and 0x7f
            remaining = remaining ushr 7
            out.write(part or if (remaining == 0) 0 else 0x80)
        } while (remaining != 0)
        return out.toByteArray()
    }
}
