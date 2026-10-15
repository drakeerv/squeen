package com.drakeerv.squeen.data.net

import com.drakeerv.squeen.BuildConfig
import com.drakeerv.squeen.data.api.SpeedQueenApi
import com.drakeerv.squeen.data.session.SessionManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    fun createOkHttp(session: SessionManager): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor(session))
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG || BuildConfig.HTTP_LOG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.BODY
                }
            )
        }
        return builder.build()
    }

    fun createRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(KotlinxJsonConverterFactory(json))
        .build()

    fun createApi(retrofit: Retrofit): SpeedQueenApi = retrofit.create(SpeedQueenApi::class.java)

    @Suppress("unused")
    private val JSON_MEDIA = "application/json".toMediaType()
}
