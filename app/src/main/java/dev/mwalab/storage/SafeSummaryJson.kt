package dev.mwalab.storage

import org.json.JSONObject

/** JSON codec for already-sanitized string metadata only. */
object SafeSummaryJson {
    fun encode(fields: Map<String, String>): String {
        val json = JSONObject()
        fields.toSortedMap().forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString()
    }

    fun decode(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        val json = JSONObject(encoded)
        return buildMap {
            json.keys().forEach { key ->
                put(key, json.getString(key))
            }
        }
    }
}
