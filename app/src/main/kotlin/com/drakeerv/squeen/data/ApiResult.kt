package com.drakeerv.squeen.data

/**
 * Small result wrapper used across the app: either a value or a user-facing
 * error with enough metadata for special cases (auth expiry, connectivity).
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>

    data class Failure(
        val message: String,
        val httpCode: Int? = null,
        val cause: Throwable? = null,
        val unauthorized: Boolean = false,
        val offline: Boolean = false,
    ) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.dataOrNull(): T? = (this as? ApiResult.Success)?.data

fun ApiResult<*>.errorMessageOrNull(): String? = (this as? ApiResult.Failure)?.message
