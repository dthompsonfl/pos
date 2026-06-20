package com.enterprise.pos.core

/**
 * Result type used throughout the entire codebase.
 * Replaces try/catch across module boundaries so failures are explicit, typed, and composable.
 */
sealed class Result<out T> {
    data class Success<out T>(val value: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (AppError) -> Unit): Result<T> {
        if (this is Failure) action(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.toException()
    }

    companion object {
        fun <T> success(value: T): Result<T> = Success(value)
        fun failure(error: AppError): Result<Nothing> = Failure(error)
        fun failure(message: String): Result<Nothing> = Failure(AppError.Generic(message))

        inline fun <T> catching(block: () -> T): Result<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(AppError.fromThrowable(t))
        }
    }
}

/**
 * Unified error model. Every error has a stable code that the UI can switch on for
 * localized messaging, plus an optional cause and provider-specific payload.
 */
sealed class AppError(open val message: String) {
    data class Generic(override val message: String) : AppError(message)
    data class NotFound(val what: String, override val message: String = "$what not found") : AppError(message)
    data class Validation(val field: String?, override val message: String) : AppError(message)
    data class Payment(val code: PaymentErrorCode, override val message: String, val providerError: String? = null) : AppError(message)
    data class Hardware(val device: String, override val message: String) : AppError(message)
    data class Network(override val message: String = "Network error") : AppError(message)
    data class Sync(val conflicts: List<String> = emptyList(), override val message: String = "Sync conflict") : AppError(message)
    data class Auth(override val message: String = "Authentication required") : AppError(message)
    data class Permission(val required: String, override val message: String = "Missing permission: $required") : AppError(message)

    fun toException(): Exception = RuntimeException(message)

    companion object {
        fun fromThrowable(t: Throwable): AppError = when (t) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException -> Network(t.message ?: "Network error")
            else -> Generic(t.message ?: t::class.java.simpleName)
        }
    }
}

enum class PaymentErrorCode {
    CANCELLED,
    DECLINED,
    CARD_READ_FAILED,
    INSUFFICIENT_FUNDS,
    NETWORK_ERROR,
    PROVIDER_NOT_CONFIGURED,
    READER_DISCONNECTED,
    AMOUNT_MISMATCH,
    REFUND_FAILED,
    UNKNOWN
}
