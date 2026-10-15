package com.drakeerv.squeen.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Small Retrofit converter that knows how to
 *
 *  - serialize any `@Serializable` request body with kotlinx.serialization
 *  - deserialize `JsonElement` responses (JSON:API documents and plain JSON)
 *  - pass through plain `String` responses (HTML payment forms)
 */
class KotlinxJsonConverterFactory(private val json: Json) : Converter.Factory() {

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? {
        val serializer = runCatching { json.serializersModule.serializer(type) }.getOrNull() ?: return null
        return Converter<Any, RequestBody> { value ->
            json.encodeToString(serializer, value).toRequestBody(JSON_MEDIA_TYPE)
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? = when (type) {
        JsonElementType -> Converter<ResponseBody, kotlinx.serialization.json.JsonElement> { body ->
            body.use { json.parseToJsonElement(it.string()) }
        }
        String::class.java -> Converter<ResponseBody, String> { body ->
            body.use { it.string() }
        }
        else -> null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JsonElementType = kotlinx.serialization.json.JsonElement::class.java
    }
}
