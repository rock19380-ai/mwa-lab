package dev.mwalab.storage.transaction

import dev.mwalab.transaction.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigInteger

/** Versioned allowlist only. No arbitrary maps, memo text, wire bytes, or exception strings. */
internal object TransactionSummaryJson {
    const val MAX_ENCODED_BYTES = 64 * 1024

    fun version(value: TransactionVersion): String = when (value) {
        TransactionVersion.LEGACY -> "LEGACY"
        TransactionVersion.V0 -> "V0"
        TransactionVersion.UNKNOWN -> "UNKNOWN"
        is TransactionVersion.VERSIONED_UNSUPPORTED -> "VERSIONED_UNSUPPORTED:${value.number}"
    }

    private fun parseVersion(value: String): TransactionVersion = when (value) {
        "LEGACY" -> TransactionVersion.LEGACY
        "V0" -> TransactionVersion.V0
        "UNKNOWN" -> TransactionVersion.UNKNOWN
        else -> {
            require(value.startsWith("VERSIONED_UNSUPPORTED:"))
            TransactionVersion.VERSIONED_UNSUPPORTED(value.substringAfter(':').toInt()).also {
                require(version(it) == value)
            }
        }
    }

    fun encode(summary: TransactionSummary): String {
        val binding = requireNotNull(summary.binding) { "Unbound diagnostics cannot be persisted" }
        val encoded = obj(
            "format" to 1, "diagnostic_id" to summary.diagnosticId,
            "session_id" to binding.sessionId, "event_id" to binding.eventId,
            "payload_index" to summary.payloadIndex, "fingerprint_sha256" to summary.fingerprintSha256,
            "wire_length" to summary.wireLength, "version" to version(summary.transactionVersion),
            "inspection_status" to summary.inspectionStatus.name, "signature_count" to summary.signatureCount,
            "header" to summary.header?.let { obj("required" to it.numRequiredSignatures,
                "readonly_signed" to it.numReadonlySignedAccounts, "readonly_unsigned" to it.numReadonlyUnsignedAccounts) },
            "recent_blockhash" to summary.recentBlockhash,
            "accounts" to summary.accounts?.let { array(it.map(::account)) },
            "instructions" to summary.instructions?.let { array(it.map(::instruction)) },
            "lookups" to summary.addressTableLookups?.let { values -> array(values.map {
                obj("index" to it.index, "table" to it.tableAccount,
                    "writable" to array(it.writableIndexes), "readonly" to array(it.readonlyIndexes))
            }) },
            "limitations" to array(summary.limitations.map { it.name }),
            "error" to summary.error?.let { obj("reason" to it.reason.name, "offset" to it.byteOffset) },
            "static_account_count" to summary.staticAccountCount, "total_account_count" to summary.totalAccountCount,
            "instruction_count" to summary.instructionCount, "fee_payer" to summary.feePayer,
            "required_signer_count" to summary.requiredSignerCount,
        ).toString()
        require(encoded.length <= MAX_ENCODED_BYTES && encoded.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES)
        return encoded
    }

    fun decode(encoded: String): TransactionSummary {
        require(encoded.length <= MAX_ENCODED_BYTES && encoded.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES)
        try {
            requireBoundedNesting(encoded)
            val tokens = JSONTokener(encoded)
            val root = tokens.nextValue() as? JSONObject ?: error("Diagnostic summary must be an object")
            require(tokens.nextClean() == '\u0000')
            require(root.int("format") == 1)
            val accounts = root.nullable("accounts")?.let { raw ->
                list(raw, TransactionInspectionLimits.MAX_ACCOUNTS) { account(it as JSONObject) }
            }
            fun reference(index: Int) = InstructionAccountReference(index, accounts?.getOrNull(index))
            val instructions = root.nullable("instructions")?.let { raw ->
                list(raw, TransactionInspectionLimits.MAX_INSTRUCTIONS) { value ->
                    val json = value as JSONObject
                    val references = list(json.get("accounts"), TransactionInspectionLimits.MAX_ACCOUNTS_PER_INSTRUCTION) {
                        reference(it as Int)
                    }
                    InstructionSummary(json.int("index"), json.int("program_index"), json.string("program_id"),
                        references, json.int("data_length"), json.string("data_sha256"),
                        decoded(json.get("decoded") as JSONObject, ::reference))
                }
            }
            val summary = TransactionSummary(
                fingerprintSha256 = root.string("fingerprint_sha256"), wireLength = root.int("wire_length"),
                transactionVersion = parseVersion(root.string("version")),
                inspectionStatus = TransactionInspectionStatus.valueOf(root.string("inspection_status")),
                signatureCount = root.nullable("signature_count")?.let { it as Int },
                header = root.nullable("header")?.let { value ->
                    val h = value as JSONObject
                    TransactionMessageHeader(h.int("required"), h.int("readonly_signed"), h.int("readonly_unsigned"))
                },
                recentBlockhash = root.nullable("recent_blockhash")?.let { it as String },
                accounts = accounts, instructions = instructions,
                addressTableLookups = root.nullable("lookups")?.let { raw ->
                    list(raw, TransactionInspectionLimits.MAX_ADDRESS_TABLE_LOOKUPS) { value ->
                        val json = value as JSONObject
                        AddressTableLookupSummary(json.int("index"), json.string("table"),
                            list(json.get("writable"), TransactionInspectionLimits.MAX_ACCOUNTS) { it as Int },
                            list(json.get("readonly"), TransactionInspectionLimits.MAX_ACCOUNTS) { it as Int })
                    }
                },
                limitations = list(root.get("limitations"), TransactionInspectionLimitation.entries.size) {
                    TransactionInspectionLimitation.valueOf(it as String)
                },
                error = root.nullable("error")?.let { value ->
                    val json = value as JSONObject
                    TransactionInspectionError(TransactionInspectionFailure.valueOf(json.string("reason")), json.int("offset"))
                },
                payloadIndex = root.int("payload_index"),
                binding = TransactionDiagnosticBinding(root.string("session_id"), root.string("event_id")),
            )
            // Detect missing/extra fields, duplicate keys, coercion, changed derived counts, and noncanonical encoding.
            require(encode(summary) == encoded) { "Diagnostic JSON must be canonical and self-consistent" }
            return summary
        } catch (_: JSONException) {
            throw IllegalArgumentException("Invalid diagnostic JSON")
        }
    }

    private fun account(value: AccountSummary) = obj("index" to value.index, "public_key" to value.publicKey,
        "signer" to value.isSigner, "writable" to value.isWritable, "fee_payer" to value.isFeePayer)
    private fun account(json: JSONObject) = AccountSummary(json.int("index"), json.string("public_key"),
        json.get("signer") as Boolean, json.get("writable") as Boolean, json.get("fee_payer") as Boolean)
    private fun instruction(value: InstructionSummary) = obj("index" to value.index,
        "program_index" to value.programIdIndex, "program_id" to value.programId,
        "accounts" to array(value.accountReferences.map { it.index }),
        "data_length" to value.dataLength, "data_sha256" to value.dataSha256,
        "decoded" to decoded(value.decodedInstruction))

    private fun decoded(value: DecodedInstruction): JSONObject = when (value) {
        DecodedInstruction.Unknown -> obj("kind" to "UNKNOWN")
        is DecodedInstruction.Unsupported -> obj("kind" to "UNSUPPORTED", "program" to value.program.name)
        is DecodedInstruction.Malformed -> obj("kind" to "MALFORMED", "program" to value.program.name, "reason" to value.reason.name)
        is DecodedInstruction.Unavailable -> obj("kind" to "UNAVAILABLE", "program" to value.program.name, "reason" to value.reason.name)
        is DecodedInstruction.SystemTransfer -> obj("kind" to "SYSTEM_TRANSFER", "from" to value.from,
            "to" to value.to, "lamports" to value.lamports.toString())
        is DecodedInstruction.Memo -> obj("kind" to "MEMO", "preview_status" to value.previewStatus.name)
        is DecodedInstruction.SplTokenTransfer -> obj("kind" to "SPL_TRANSFER", "source" to value.source,
            "destination" to value.destination, "authority" to authority(value.authority), "raw_amount" to value.rawAmount.toString())
        is DecodedInstruction.SplTokenTransferChecked -> obj("kind" to "SPL_TRANSFER_CHECKED",
            "source" to value.source, "mint" to value.mint, "destination" to value.destination,
            "authority" to authority(value.authority), "raw_amount" to value.rawAmount.toString(),
            "declared_decimals" to value.declaredDecimals)
    }

    private fun decoded(json: JSONObject, reference: (Int) -> InstructionAccountReference): DecodedInstruction =
        when (json.string("kind")) {
            "UNKNOWN" -> DecodedInstruction.Unknown
            "UNSUPPORTED" -> DecodedInstruction.Unsupported(KnownProgram.valueOf(json.string("program")))
            "MALFORMED" -> DecodedInstruction.Malformed(KnownProgram.valueOf(json.string("program")),
                InstructionDecodingFailure.valueOf(json.string("reason")))
            "UNAVAILABLE" -> DecodedInstruction.Unavailable(KnownProgram.valueOf(json.string("program")),
                InstructionDecodingFailure.valueOf(json.string("reason")))
            "SYSTEM_TRANSFER" -> DecodedInstruction.SystemTransfer(json.string("from"), json.string("to"), amount(json, "lamports"))
            "MEMO" -> DecodedInstruction.Memo(MemoPreviewStatus.valueOf(json.string("preview_status")))
            "SPL_TRANSFER" -> DecodedInstruction.SplTokenTransfer(json.string("source"), json.string("destination"),
                authority(json.get("authority") as JSONObject, reference), amount(json, "raw_amount"))
            "SPL_TRANSFER_CHECKED" -> DecodedInstruction.SplTokenTransferChecked(json.string("source"), json.string("mint"),
                json.string("destination"), authority(json.get("authority") as JSONObject, reference),
                amount(json, "raw_amount"), json.int("declared_decimals"))
            else -> throw IllegalArgumentException("Unknown decoded instruction kind")
        }

    private fun authority(value: TokenAuthority) = obj("public_key" to value.publicKey,
        "multisig" to array(value.multisigSignerReferences.map { it.index }))
    private fun authority(json: JSONObject, reference: (Int) -> InstructionAccountReference) =
        TokenAuthority(json.string("public_key"),
            list(json.get("multisig"), TransactionInspectionLimits.MAX_ACCOUNTS_PER_INSTRUCTION) { reference(it as Int) })
    private fun amount(json: JSONObject, key: String): BigInteger =
        BigInteger(json.string(key)).also { require(it.toString() == json.string(key)) }

    private fun requireBoundedNesting(encoded: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in encoded) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted && !escaped)
    }

    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    private fun array(values: List<*>) = JSONArray(values)
    private fun JSONObject.int(key: String): Int = get(key) as? Int ?: throw IllegalArgumentException("Expected integer metadata")
    private fun JSONObject.string(key: String): String = get(key) as? String ?: throw IllegalArgumentException("Expected string metadata")
    private fun JSONObject.nullable(key: String): Any? = get(key).let { if (it === JSONObject.NULL) null else it }
    private fun <T> list(raw: Any, maximum: Int, map: (Any) -> T): List<T> {
        val array = raw as? JSONArray ?: throw IllegalArgumentException("Expected diagnostic array")
        require(array.length() <= maximum)
        return (0 until array.length()).map { map(array.get(it)) }
    }
}

internal fun TransactionSummary.toEntity(): TransactionDiagnosticEntity = TransactionDiagnosticEntity(
    diagnosticId = requireNotNull(diagnosticId), sessionId = requireNotNull(sessionId),
    eventId = requireNotNull(eventId), payloadIndex = payloadIndex,
    fingerprintSha256 = fingerprintSha256, wireLength = wireLength,
    version = TransactionSummaryJson.version(transactionVersion), inspectionStatus = inspectionStatus.name,
    summaryJson = TransactionSummaryJson.encode(this),
)

internal fun TransactionDiagnosticEntity.toDomain(): TransactionSummary = TransactionSummaryJson.decode(summaryJson).also {
    require(diagnosticId == it.diagnosticId && sessionId == it.sessionId && eventId == it.eventId &&
        payloadIndex == it.payloadIndex && fingerprintSha256 == it.fingerprintSha256 && wireLength == it.wireLength &&
        version == TransactionSummaryJson.version(it.transactionVersion) && inspectionStatus == it.inspectionStatus.name)
}
