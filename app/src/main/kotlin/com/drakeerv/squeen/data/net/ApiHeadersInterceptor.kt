package com.drakeerv.squeen.data.net

import com.drakeerv.squeen.BuildConfig
import com.drakeerv.squeen.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Mirrors the global header interceptor of the original client:
 *
 *  - `x-api-key` static application key
 *  - `APP`        flavour code (upper case)
 *  - `alliancels-organization-id` selected organization/geo-boundary
 *  - `x-ref-app`  static reference
 *  - `alliancels-auth-token`    session token (when present)
 */
class ApiHeadersInterceptor(private val session: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("x-api-key", BuildConfig.API_KEY)
            .header("APP", BuildConfig.APP_CODE.uppercase())
            .header("x-ref-app", BuildConfig.REF_APP)

        session.organizationId.takeIf { it.isNotEmpty() }?.let {
            builder.header("alliancels-organization-id", it)
        }
        session.token.takeIf { it.isNotEmpty() }?.let {
            builder.header("alliancels-auth-token", it)
        }
        return chain.proceed(builder.build())
    }
}
