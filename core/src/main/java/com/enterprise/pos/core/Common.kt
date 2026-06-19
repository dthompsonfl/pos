package com.enterprise.pos.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Time provider abstraction — lets us write deterministic tests by injecting a fake clock. */
fun interface Clock {
    fun now(): Long
    fun isoNow(): String = java.time.Instant.ofEpochMilli(now()).toString()
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

/** Dispatcher abstraction for testability. */
interface DispatcherProvider {
    fun io(): kotlinx.coroutines.CoroutineDispatcher
    fun default(): kotlinx.coroutines.CoroutineDispatcher
    fun main(): kotlinx.coroutines.CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override fun io() = kotlinx.coroutines.Dispatchers.IO
    override fun default() = kotlinx.coroutines.Dispatchers.Default
    override fun main() = kotlinx.coroutines.Dispatchers.Main
}

/** Logger interface — pluggable for production (Timber) or test (in-memory). */
interface Logger {
    fun d(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

object NoopLogger : Logger {
    override fun d(tag: String, message: String, throwable: Throwable?) {}
    override fun i(tag: String, message: String, throwable: Throwable?) {}
    override fun w(tag: String, message: String, throwable: Throwable?) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}

/** Convert a suspending block into a Flow<Result<T>> for reactive UI consumption. */
inline fun <T> resultFlow(crossinline block: suspend () -> T): Flow<Result<T>> = flow {
    emit(Result.success(block()))
}.catch { throwable ->
    emit(Result.failure(AppError.fromThrowable(throwable)))
}

/** Convert a Flow<T> to Flow<Result<T>>. */
fun <T> Flow<T>.asResults(): Flow<Result<T>> =
    map { Result.success(it) }
        .catch { emit(Result.failure(AppError.fromThrowable(it))) }
