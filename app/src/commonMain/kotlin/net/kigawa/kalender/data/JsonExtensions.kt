package net.kigawa.kalender.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

// contentOrNull() is now provided by kotlinx.serialization.json library

fun JsonElement.jsonArray(key: String): JsonArray {
    return this.jsonObject()[key]!!.jsonArray()
}

fun JsonElement.jsonObject(key: String): JsonObject {
    return this.jsonObject()[key]!!.jsonObject()
}

fun JsonElement.jsonArray(): JsonArray {
    return this as JsonArray
}

fun JsonElement.jsonObject(): JsonObject {
    return this as JsonObject
}

// jsonPrimitive is provided by kotlinx.serialization.json as a property in newer versions
// For compatibility, we provide it as an extension property
val JsonElement.jsonPrimitive: JsonPrimitive?
    get() = this as? JsonPrimitive

val JsonElement.jsonArray: JsonArray?
    get() = this as? JsonArray

/**
 * JsonPrimitive のリストから JsonArray を構築する。
 * 文字列連結して parseToJsonElement する方法は、`content` が JSON 引用符を
 * 含まないため `[RRULE:...]` のような不正JSONになり即例外になる。
 */
fun List<JsonPrimitive>.toJsonArray(): JsonArray = JsonArray(this)

fun JsonElement.optJSONArray(key: String): JsonArray? {
    return this.jsonObject()[key]?.jsonArray()
}

fun JsonElement.optJSONObject(key: String): JsonObject? {
    return this.jsonObject()[key]?.jsonObject()
}

fun JsonElement.optString(key: String, default: String = ""): String {
    return this.jsonObject()[key]?.jsonPrimitive?.content ?: default
}

fun JsonObject.optString(key: String, default: String = ""): String {
    return this[key]?.jsonPrimitive?.content ?: default
}

fun JsonObject.optJSONObject(key: String): JsonObject? {
    return this[key]?.jsonObject()
}