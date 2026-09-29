package dev.mwalab.transaction

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Strict UTF-8, at most 256 code points in an escaped transient preview; durable text is forbidden. */
internal class MemoProgramDecoder : ProgramDecoder {
    override val program = KnownProgram.MEMO

    override fun decode(accounts: List<InstructionAccountReference>, data: ByteArray): InstructionDecoding {
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
        } catch (_: CharacterCodingException) {
            return InstructionDecoding(DecodedInstruction.Memo(MemoPreviewStatus.INVALID_UTF8))
        }
        val preview = StringBuilder()
        var offset = 0
        var count = 0
        while (offset < text.length) {
            val codePoint = Character.codePointAt(text, offset)
            offset += Character.charCount(codePoint)
            // Check all text, including beyond truncation. FORMAT includes bidi and invisible controls.
            val category = Character.getType(codePoint)
            if (codePoint != '\n'.code && codePoint != '\t'.code &&
                (category == Character.CONTROL.toInt() || category == Character.FORMAT.toInt() ||
                    category == Character.LINE_SEPARATOR.toInt() || category == Character.PARAGRAPH_SEPARATOR.toInt())) {
                return InstructionDecoding(DecodedInstruction.Memo(MemoPreviewStatus.UNSAFE_TEXT))
            }
            if (count < MAX_PREVIEW_CODE_POINTS) {
                when (codePoint) {
                    '\n'.code -> preview.append("\\n")
                    '\t'.code -> preview.append("\\t")
                    '\\'.code -> preview.append("\\\\")
                    else -> preview.appendCodePoint(codePoint)
                }
            }
            count++
        }
        val truncated = count > MAX_PREVIEW_CODE_POINTS
        return InstructionDecoding(DecodedInstruction.Memo(
            if (truncated) MemoPreviewStatus.TRUNCATED else MemoPreviewStatus.DISPLAYABLE),
            MemoPreview(preview.toString(), truncated))
    }

    companion object { const val MAX_PREVIEW_CODE_POINTS = 256 }
}
