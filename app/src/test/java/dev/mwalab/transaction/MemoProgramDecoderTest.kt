package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer
import org.junit.Assert.*
import org.junit.Test

class MemoProgramDecoderTest {
    private val fixtures = DecoderTestFixtures

    @Test
    fun validMemoHasStrictTransientPreviewAndTextFreeMetadata() {
        val data = "MWA Lab test".encodeToByteArray()
        val decoded = fixtures.decodeMemo(data)
        assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.DISPLAYABLE), decoded.decodedInstruction)
        assertEquals("MWA Lab test", decoded.memoPreview!!.text)
        assertFalse(decoded.memoPreview.truncated)
        assertFalse(decoded.memoPreview.toString().contains("MWA Lab test"))
        val instruction = fixtures.inspect(KnownProgram.MEMO, data, emptyList(), keyCount = 2)
        assertEquals("Memo Program", instruction.programName)
        assertEquals("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr", instruction.programId)
        assertEquals(12, instruction.dataLength)
        assertEquals("92b9b73443cf8e43f7279fa178af2739d3ed26c89c61664e6b879ba7d6b50839", instruction.dataSha256)
        assertEquals(decoded.decodedInstruction, instruction.decodedInstruction)
        assertFalse(instruction.decodedInstruction.toString().contains("MWA Lab test"))
    }

    @Test
    fun newlineTabAndEscapeCharactersAreUnambiguousInThePreview() {
        val decoded = fixtures.decodeMemo("line\nnext\t\\n".encodeToByteArray())
        assertEquals("line\\nnext\\t\\\\n", decoded.memoPreview!!.text)
        assertFalse(decoded.memoPreview.text.any { it.isISOControl() })
        assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.DISPLAYABLE), decoded.decodedInstruction)
    }

    @Test
    fun maximumPreviewIsMeasuredInUnicodeCodePointsAndTruncationIsExplicit() {
        val emoji = "\uD83D\uDE00"
        val boundary = fixtures.decodeMemo(emoji.repeat(256).encodeToByteArray())
        assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.DISPLAYABLE), boundary.decodedInstruction)
        assertEquals(256, boundary.memoPreview!!.text.codePointCount(0, boundary.memoPreview.text.length))
        assertFalse(boundary.memoPreview.truncated)
        val data = emoji.repeat(257).encodeToByteArray()
        val longer = fixtures.decodeMemo(data)
        assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.TRUNCATED), longer.decodedInstruction)
        assertEquals(emoji.repeat(256), longer.memoPreview!!.text)
        assertTrue(longer.memoPreview.truncated)
        val summary = fixtures.inspect(KnownProgram.MEMO, data, emptyList(), keyCount = 2)
        assertEquals(1028, summary.dataLength)
        assertEquals(DiagnosticSanitizer.sha256(data), summary.dataSha256)
    }

    @Test
    fun malformedUtf8IsNeverReplacementDecodedOrRetained() {
        for (data in listOf(byteArrayOf(0x80.toByte()), byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xe2.toByte(), 0x82.toByte()))) {
            val decoded = fixtures.decodeMemo(data)
            assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.INVALID_UTF8), decoded.decodedInstruction)
            assertNull(decoded.memoPreview)
            val instruction = fixtures.inspect(KnownProgram.MEMO, data, emptyList(), keyCount = 2)
            assertEquals(data.size, instruction.dataLength)
            assertEquals(DiagnosticSanitizer.sha256(data), instruction.dataSha256)
        }
    }

    @Test
    fun controlsBidiAndInvisibleFormatCharactersSuppressTheCompletePreview() {
        for (text in listOf("x\u0000y", "x\ry", "x\u007fy", "x\u0085y", "x\u202ey",
            "x\u2066y", "x\u200by", "x\u2028y", "x\u2029y", "a".repeat(257) + "\u202e")) {
            val decoded = fixtures.decodeMemo(text.encodeToByteArray())
            assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.UNSAFE_TEXT), decoded.decodedInstruction)
            assertNull(decoded.memoPreview)
        }
    }

    @Test
    fun emptyMemoIsRepresentableAndUnregisteredMemoIdsRemainUnknown() {
        val empty = fixtures.decodeMemo(byteArrayOf())
        assertEquals(DecodedInstruction.Memo(MemoPreviewStatus.DISPLAYABLE), empty.decodedInstruction)
        assertEquals("", empty.memoPreview!!.text)
        // The registry supports the Demo Client's Memo ID only, not aliases inferred from data.
        val bytes = fixtures.transaction("Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo", "test".encodeToByteArray())
        val instruction = TransactionInspector().inspect(bytes).instructions!!.single()
        assertNull(instruction.programName)
        assertEquals(DecodedInstruction.Unknown, instruction.decodedInstruction)
    }
}
