package com.enterprise.pos.data.sync

import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Production sync backend — POSTs outbox events to your POS backend.
 *
 * Endpoints (must be implemented by your backend):
 *   POST /v1/sync/events            — send an outbox event with idempotency key
 *   POST /v1/sync/events/{id}/resolve — resolve a conflict
 *
 * Responses:
 *   200/201 → Accepted (event deleted from outbox)
 *   409 + body → Conflict (server has a different version; surface to UI)
 *   410 + body → Duplicate (server already processed; event deleted)
 *   422 → Rejected (permanent failure; event marked FAILED)
 *   5xx / network error → retried with backoff
 */
class HttpSyncBackend(
    private val baseUrl: String,
    private val authTokenProvider: com.enterprise.pos.core.security.AuthTokenProvider,
    private val logger: Logger = NoopLogger
) : SyncBackend {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(this@HttpSyncBackend.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    @Serializable
    private data class SyncEventRequest(
        val eventId: String,
        val storeId: String,
        val registerId: String?,
        val employeeId: String?,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val schemaVersion: Int,
        val idempotencyKey: String,
        val payloadJson: String,
        val createdAt: Long
    )

    @Serializable
    private data class SyncConflictResponse(val serverVersionJson: String, val reason: String)

    @Serializable
    private data class SyncResolveRequest(val resolution: String, val localVersionJson: String)

    override suspend fun send(event: SyncOutboxEntity): SyncBackendResponse {
        return try {
            val response: HttpResponse = client.post("$baseUrl/v1/sync/events") {
                contentType(ContentType.Application.Json)
                authTokenProvider.getToken()?.takeIf { it.isNotBlank() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                header("Idempotency-Key", event.idempotencyKey)
                setBody(SyncEventRequest(
                    eventId = event.id,
                    storeId = event.storeId,
                    registerId = event.registerId,
                    employeeId = event.employeeId,
                    entityType = event.entityType,
                    entityId = event.entityId,
                    operation = event.operation,
                    schemaVersion = event.schemaVersion,
                    idempotencyKey = event.idempotencyKey,
                    payloadJson = event.payloadJson,
                    createdAt = event.createdAt
                ))
            }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.NoContent ->
                    SyncBackendResponse.Accepted
                HttpStatusCode.Conflict -> {
                    val body = response.bodyAsText()
                    val parsed = runCatching { json.decodeFromString(SyncConflictResponse.serializer(), body) }.getOrNull()
                    SyncBackendResponse.Conflict(parsed?.serverVersionJson ?: body)
                }
                HttpStatusCode.Gone -> SyncBackendResponse.Duplicate(event.id)
                HttpStatusCode.UnprocessableEntity -> {
                    val body = response.bodyAsText()
                    SyncBackendResponse.Rejected("Server rejected: $body")
                }
                else -> {
                    val body = response.bodyAsText()
                    SyncBackendResponse.Rejected("HTTP ${response.status.value}: $body")
                }
            }
        } catch (t: Throwable) {
            logger.w(TAG, "Sync send failed for ${event.id}", t)
            throw t
        }
    }

    override suspend fun resolve(event: SyncOutboxEntity, resolution: ConflictResolution): SyncBackendResponse {
        return try {
            val response: HttpResponse = client.post("$baseUrl/v1/sync/events/${event.id}/resolve") {
                contentType(ContentType.Application.Json)
                authTokenProvider.getToken()?.takeIf { it.isNotBlank() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(SyncResolveRequest(
                    resolution = resolution.name,
                    localVersionJson = event.payloadJson
                ))
            }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.NoContent -> SyncBackendResponse.Accepted
                HttpStatusCode.Conflict -> {
                    val body = response.bodyAsText()
                    SyncBackendResponse.RequiresManualResolution(body)
                }
                else -> SyncBackendResponse.Rejected("HTTP ${response.status.value}")
            }
        } catch (t: Throwable) {
            logger.w(TAG, "Conflict resolution failed for ${event.id}", t)
            throw t
        }
    }

    companion object { private const val TAG = "HttpSyncBackend" }
}
