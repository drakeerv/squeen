package com.drakeerv.squeen.data.jsonapi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Minimal, allocation-friendly JSON:API 1.0 reader.
 *
 * The Speed Queen API answers with documents shaped like
 * ```
 * { "jsonapi": {...}, "data": { "type": "...", "id": "...",
 *   "attributes": {...}, "relationships": {...} }, "included": [ ... ] }
 * ```
 * `data` may be a single resource or an array. Relationship members either carry
 * an identifier object (`data: {type, id}`), an array of identifier objects or a
 * missing/null `data`.
 */
class JsonApiDocument(val root: JsonObject) {

    val dataElement: JsonElement?
        get() = root["data"]

    val included: List<JsonApiResource> by lazy {
        root["included"]?.safeArray()?.mapNotNull { it.safeObject() }?.map { JsonApiResource(it, this) } ?: emptyList()
    }

    /** Single resource (`data` is an object). */
    fun resource(): JsonApiResource? = dataElement?.safeObject()?.let { JsonApiResource(it, this) }

    /** Resource collection (`data` is an array). Empty when data is null. */
    fun resources(): List<JsonApiResource> {
        val array = dataElement?.safeArray()
        if (array != null) {
            return array.mapNotNull { it.safeObject() }.map { JsonApiResource(it, this) }
        }
        // A single resource is also accepted where a collection is expected.
        return resource()?.let { listOf(it) } ?: emptyList()
    }

    /** Resolve a resource from the `included` set, or from `data` when it matches. */
    fun lookup(type: String, id: String): JsonApiResource? {
        included.firstOrNull { it.type == type && it.id == id }?.let { return it }
        val direct = resource()
        if (direct != null && direct.type == type && direct.id == id) return direct
        return included.firstOrNull { it.id == id }
    }

    companion object {
        fun of(element: JsonElement?): JsonApiDocument =
            JsonApiDocument(element?.safeObject() ?: JsonObject(emptyMap()))
    }
}

class JsonApiResource(val obj: JsonObject, private val document: JsonApiDocument) {

    val type: String get() = obj["type"].primitiveContent() ?: ""
    val id: String get() = obj["id"].primitiveContent() ?: ""

    private val attributes: JsonObject get() = obj["attributes"]?.safeObject() ?: EMPTY
    private val relationships: JsonObject get() = obj["relationships"]?.safeObject() ?: EMPTY

    fun hasAttribute(name: String): Boolean = attributes.containsKey(name)

    fun str(name: String): String? =
        attributes[name].primitiveContent() ?: obj[name].primitiveContent()

    fun int(name: String): Int? =
        attributes[name].primitiveInt() ?: obj[name].primitiveInt()

    fun long(name: String): Long? =
        attributes[name]?.safePrimitive()?.longOrNull ?: obj[name]?.safePrimitive()?.longOrNull

    fun double(name: String): Double? =
        attributes[name]?.safePrimitive()?.doubleOrNull ?: obj[name]?.safePrimitive()?.doubleOrNull

    fun bool(name: String): Boolean? =
        attributes[name]?.safePrimitive()?.booleanOrNull ?: obj[name]?.safePrimitive()?.booleanOrNull

    fun obj(name: String): JsonObject? = attributes[name]?.safeObject() ?: obj[name]?.safeObject()

    fun array(name: String): JsonArray? = attributes[name]?.safeArray()

    /** All attributes of a nested object member, if present. */
    fun objAttributes(name: String): JsonObject? = obj(name)?.get("attributes")?.safeObject() ?: obj(name)

    // ------------------------------------------------------ relationships

    private fun relData(name: String): JsonElement? = relationships[name]?.safeObject()?.get("data")

    /** Id of a to-one relationship; also tolerates `{"id": "..."}` in attributes. */
    fun relId(name: String): String? {
        relData(name)?.safeObject()?.let { rel ->
            rel["id"].primitiveContent()?.let { return it }
        }
        attributes[name]?.safeObject()?.get("id").primitiveContent()?.let { return it }
        obj[name].primitiveContent()?.let { return it }
        return null
    }

    fun relIds(name: String): List<String> =
        relData(name)?.safeArray()?.mapNotNull { it.safeObject()?.get("id").primitiveContent() } ?: emptyList()

    /** Resolve a to-one relationship against the document payload. */
    fun rel(name: String): JsonApiResource? {
        val relElement = relData(name)?.safeObject() ?: attributes[name]?.safeObject() ?: return null
        val relType = relElement["type"].primitiveContent() ?: name
        val relId = relElement["id"].primitiveContent() ?: return null
        return document.lookup(relType, relId)
    }

    /** Resolve a to-many relationship against the document payload. */
    fun relList(name: String): List<JsonApiResource> {
        val array = relData(name)?.safeArray() ?: return emptyList()
        return array.mapNotNull { el ->
            val rel = el.safeObject() ?: return@mapNotNull null
            val relType = rel["type"].primitiveContent() ?: name
            val relId = rel["id"].primitiveContent() ?: return@mapNotNull null
            document.lookup(relType, relId)
        }
    }

    companion object {
        private val EMPTY = JsonObject(emptyMap())
    }
}

// ------------------------------------------------------------- primitives

internal fun JsonElement?.safeObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.safeArray(): JsonArray? = this as? JsonArray

internal fun JsonElement?.safePrimitive(): JsonPrimitive? = this as? JsonPrimitive

internal fun JsonElement?.primitiveContent(): String? {
    val primitive = safePrimitive() ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.let { if (it == "null") null else it }
}

internal fun JsonElement?.primitiveInt(): Int? = safePrimitive()?.takeIf { it !is JsonNull }?.intOrNull

internal fun JsonElement?.primitiveBool(): Boolean? = safePrimitive()?.takeIf { it !is JsonNull }?.booleanOrNull
