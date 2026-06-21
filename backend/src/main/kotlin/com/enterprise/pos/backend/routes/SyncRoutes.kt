package com.enterprise.pos.backend.routes

import com.enterprise.pos.backend.storage.SyncEvent
import com.enterprise.pos.backend.storage.SyncEventStatus
import com.enterprise.pos.backend.storage.SyncEventStore
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Request / Response DTOs
// ---------------------------------------------------------------------------

@Serializable
data class SyncEventRequest(
    val eventId: String,
    val merchantId: String? = null,
    val storeId: String,
    val registerId: String? = null,
    val employeeId: String? = null,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val schemaVersion: Int,
    val idempotencyKey: String,
    val payloadJson: String,
    val createdAt: Long
)

@Serializable
data class SyncEventResponse(
    val status: String,
    val message: String? = null,
    val serverVersionJson: String? = null
)

@Serializable
data class SyncResolveRequest(
    val resolution: String,
    val localVersionJson: String? = null
)

@Serializable
data class SyncQueueAckRequest(
    val eventIds: List<String>
)

@Serializable
data class SyncQueueAckResponse(
    val acknowledged: Int,
    val eventIds: List<String>
)

@Serializable
data class SyncEventListResponse(
    val events: List<SyncEvent>,
    val count: Int
)

private suspend fun ApplicationCall.requireMerchantId(value: String? = null): String? {
    val merchantId = request.headers["X-Merchant-Id"]?.takeIf { it.isNotBlank() }
        ?: value?.takeIf { it.isNotBlank() }
    if (merchantId == null) {
        respond(
            HttpStatusCode.BadRequest,
            SyncEventResponse(status = "REJECTED", message = "X-Merchant-Id is required")
        )
    }
    return merchantId
}

// ---------------------------------------------------------------------------
// Route wiring
// ---------------------------------------------------------------------------

/**
 * Sync event routes.
 *
 * Rate limiting note: these endpoints should be protected by a rate limiter
 * (e.g., Ktor RateLimit plugin or a reverse proxy like nginx) to prevent
 * abuse of the event ingestion endpoint. A reasonable limit is 100 requests
 * per minute per store.
 */
fun Route.syncEventsRoute() {
    val store = SyncEventStore()

    authenticate("pos-api-key") {

        // POST /v1/sync/events — ingest an outbox event from the POS
        post("/v1/sync/events") {
            val req = call.receive<SyncEventRequest>()
            val merchantId = call.requireMerchantId(req.merchantId) ?: return@post

            val event = SyncEvent(
                eventId = req.eventId,
                merchantId = merchantId,
                storeId = req.storeId,
                registerId = req.registerId,
                employeeId = req.employeeId,
                entityType = req.entityType,
                entityId = req.entityId,
                operation = req.operation,
                schemaVersion = req.schemaVersion,
                idempotencyKey = req.idempotencyKey,
                payloadJson = req.payloadJson,
                createdAt = req.createdAt
            )

            val result = store.storeEvent(event)
            result.fold(
                onSuccess = { storedEvent ->
                    when (storedEvent.status) {
                        SyncEventStatus.DUPLICATE ->
                            call.respond(
                                HttpStatusCode.OK,
                                SyncEventResponse(
                                    status = "DUPLICATE",
                                    message = "Already processed as ${storedEvent.eventId}"
                                )
                            )
                        else ->
                            call.respond(
                                HttpStatusCode.Accepted,
                                SyncEventResponse(
                                    status = "ACCEPTED",
                                    message = "Event accepted and applied"
                                )
                            )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        SyncEventResponse(
                            status = "REJECTED",
                            message = error.message ?: "Invalid sync event"
                        )
                    )
                }
            )
        }

        // POST /v1/sync/events/{id}/resolve — store conflict resolution choice
        post("/v1/sync/events/{id}/resolve") {
            val merchantId = call.requireMerchantId() ?: return@post
            val id = call.parameters["id"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SyncEventResponse(status = "REJECTED", message = "Missing event id")
                )
            val existing = store.getEvent(id)
            if (existing == null || existing.merchantId != merchantId) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    SyncEventResponse(status = "REJECTED", message = "Event $id not found")
                )
            }
            val req = call.receive<SyncResolveRequest>()

            val result = store.resolveEvent(id, req.resolution, req.localVersionJson)
            result.fold(
                onSuccess = { resolvedEvent ->
                    call.respond(
                        HttpStatusCode.OK,
                        SyncEventResponse(
                            status = "RESOLVED",
                            message = "Event $id resolved with ${req.resolution}",
                            serverVersionJson = resolvedEvent.serverVersionJson
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        SyncEventResponse(
                            status = "REJECTED",
                            message = error.message ?: "Resolution failed"
                        )
                    )
                }
            )
        }

        // GET /v1/sync/events — query events by storeId, status, date range
        get("/v1/sync/events") {
            val merchantId = call.requireMerchantId(call.request.queryParameters["merchantId"]) ?: return@get
            val storeId = call.request.queryParameters["storeId"]
            val statusParam = call.request.queryParameters["status"]
            val from = call.request.queryParameters["from"]?.toLongOrNull()
            val to = call.request.queryParameters["to"]?.toLongOrNull()

            val status = statusParam?.let { param ->
                try {
                    SyncEventStatus.valueOf(param.uppercase())
                } catch (_: IllegalArgumentException) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        SyncEventResponse(status = "REJECTED", message = "Invalid status: $statusParam")
                    )
                }
            }

            val events = store.queryEvents(
                merchantId = merchantId,
                storeId = storeId,
                status = status,
                fromTimestamp = from,
                toTimestamp = to
            )

            call.respond(SyncEventListResponse(events = events, count = events.size))
        }

        // GET /v1/sync/events/{id} — get a specific event
        get("/v1/sync/events/{id}") {
            val merchantId = call.requireMerchantId() ?: return@get
            val id = call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    SyncEventResponse(status = "REJECTED", message = "Missing event id")
                )

            val event = store.getEvent(id)
            if (event != null && event.merchantId == merchantId) {
                call.respond(event)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    SyncEventResponse(status = "REJECTED", message = "Event $id not found")
                )
            }
        }

        // GET /v1/sync/queue/{storeId} — get pending events for a store (POS pull)
        get("/v1/sync/queue/{storeId}") {
            val merchantId = call.requireMerchantId() ?: return@get
            val storeId = call.parameters["storeId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    SyncEventResponse(status = "REJECTED", message = "Missing storeId")
                )

            val events = store.getPendingEvents(storeId, merchantId)
            call.respond(SyncEventListResponse(events = events, count = events.size))
        }

        // POST /v1/sync/queue/{storeId}/ack — acknowledge events as processed
        post("/v1/sync/queue/{storeId}/ack") {
            val merchantId = call.requireMerchantId() ?: return@post
            val storeId = call.parameters["storeId"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SyncEventResponse(status = "REJECTED", message = "Missing storeId")
                )
            val req = call.receive<SyncQueueAckRequest>()

            val result = store.acknowledgeEvents(storeId, req.eventIds, merchantId)
            result.fold(
                onSuccess = { ackCount ->
                    call.respond(
                        HttpStatusCode.OK,
                        SyncQueueAckResponse(
                            acknowledged = ackCount,
                            eventIds = req.eventIds
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        SyncEventResponse(
                            status = "REJECTED",
                            message = error.message ?: "Acknowledge failed"
                        )
                    )
                }
            )
        }
    }
}
