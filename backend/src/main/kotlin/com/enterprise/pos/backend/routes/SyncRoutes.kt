package com.enterprise.pos.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SyncEventRequest(
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
data class SyncEventResponse(
    val status: String, // ACCEPTED | DUPLICATE | CONFLICT | REJECTED
    val message: String? = null,
    val serverVersionJson: String? = null
)

@Serializable
data class SyncResolveRequest(val resolution: String, val localVersionJson: String)

/**
 * Process-local idempotency cache used only to reject duplicate requests while the durable
 * sync store is not configured. Production deployments must replace this with a database
 * unique constraint or Redis before accepting events.
 */
private val processedKeys = ConcurrentHashMap<String, String>() // key -> eventId

/** POST /v1/sync/events — receive an outbox event from the POS. */
fun Route.syncEventsRoute() {
    authenticate("pos-api-key") {
        post("/v1/sync/events") {
            val req = call.receive<SyncEventRequest>()
            // Idempotency check
            val existingEventId = processedKeys[req.idempotencyKey]
            if (existingEventId != null) {
                call.respond(SyncEventResponse(status = "DUPLICATE", message = "Already processed as $existingEventId"))
                return@post
            }
            if (req.entityType.isBlank() || req.entityId.isBlank() || req.operation.isBlank()) {
                call.respond(HttpStatusCode.UnprocessableEntity, SyncEventResponse(status = "REJECTED", message = "Invalid sync event"))
                return@post
            }
            processedKeys[req.idempotencyKey] = req.eventId
            call.respond(
                HttpStatusCode.NotImplemented,
                SyncEventResponse(
                    status = "REJECTED",
                    message = "Durable backend sync persistence is not configured; event was not applied"
                )
            )
        }

        post("/v1/sync/events/{id}/resolve") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<SyncResolveRequest>()
            call.respond(
                HttpStatusCode.NotImplemented,
                SyncEventResponse(
                    status = "REJECTED",
                    message = "Conflict resolution store is not configured for $id via ${req.resolution}"
                )
            )
        }
    }
}
