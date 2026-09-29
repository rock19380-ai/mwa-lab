package dev.mwalab.transaction

import java.io.ByteArrayOutputStream

/** Fixed transaction framing and instruction bytes, with caller-supplied public fee payer only. */
internal object TransactionApprovalTestVectors {
    fun systemTransfer(payer: ByteArray = ByteArray(32) { 0x11 }, knownProgram: Boolean = true,
        versioned: Boolean = false, readonlyPayer: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(1); out.write(ByteArray(64))
        if (versioned) out.write(0x80)
        out.write(1); out.write(if (readonlyPayer) 1 else 0); out.write(1)
        out.write(3); out.write(payer); out.write(ByteArray(32) { 0x22 })
        out.write(ByteArray(32) { if (knownProgram) 0 else 0x55 })
        out.write(ByteArray(32) { 0x77 })
        out.write(1); out.write(2); out.write(2); out.write(0); out.write(1); out.write(12)
        out.write(byteArrayOf(2, 0, 0, 0, 0x80.toByte(), 0x96.toByte(), 0x98.toByte(), 0, 0, 0, 0, 0))
        if (versioned) out.write(0)
        return out.toByteArray()
    }
}
