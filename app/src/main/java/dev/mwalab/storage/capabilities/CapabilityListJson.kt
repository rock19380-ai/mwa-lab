package dev.mwalab.storage.capabilities

import dev.mwalab.capabilities.CapabilitySnapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONTokener

/** Canonical ordered string arrays only; never coerce arbitrary JSON values. */
internal object CapabilityListJson {
    private const val MAX_ENCODED_LENGTH = 16 * 1024

    fun encode(values: List<String>): String {
        require(values.size <= CapabilitySnapshot.MAX_COLLECTION_SIZE) { "Capability list is too long" }
        require(values.all { it.length <= 128 && it.none(Char::isISOControl) }) {
            "Invalid capability list entry"
        }
        return JSONArray(values).toString()
    }

    fun decode(encoded: String): List<String> {
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Capability JSON is too long" }
        try {
            val tokens = JSONTokener(encoded)
            val array = tokens.nextValue() as? JSONArray
                ?: throw IllegalArgumentException("Capability JSON must be an array")
            require(tokens.nextClean() == '\u0000') { "Trailing capability JSON" }
            require(array.length() <= CapabilitySnapshot.MAX_COLLECTION_SIZE) {
                "Capability list is too long"
            }
            val values = (0 until array.length()).map { index ->
                array.get(index) as? String
                    ?: throw IllegalArgumentException("Capability JSON must contain strings")
            }
            require(encode(values) == encoded) { "Capability JSON must be canonical" }
            return values
        } catch (_: JSONException) {
            throw IllegalArgumentException("Invalid capability JSON")
        }
    }
}
