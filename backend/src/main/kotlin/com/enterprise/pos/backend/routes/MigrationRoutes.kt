package com.enterprise.pos.backend.routes

import com.enterprise.pos.backend.config.BackendConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class OAuthStartResponse(val authUrl: String, val state: String)

@Serializable
data class MigrationJobResponse(
    val id: String,
    val status: String,
    val totalRecords: Int,
    val processedRecords: Int,
    val failedRecords: Int,
    val conflicts: List<MigrationConflictResponse> = emptyList()
)

@Serializable
data class MigrationConflictResponse(
    val id: String,
    val externalId: String,
    val reason: String,
    val resolution: String? = null
)

/**
 * Migration OAuth flows — these endpoints NEVER accept raw provider access tokens in the
 * request body. Instead, the merchant is redirected to the provider's OAuth consent screen,
 * and the provider redirects back to our callback with a code that we exchange for an
 * access token SERVER-SIDE. The token is then stored in our token vault (encrypted at rest).
 *
 * The Android app only sees job IDs and progress counts — never tokens.
 */
fun Route.migrationRoutes() {
    val config = BackendConfig.fromEnv()

    authenticate("pos-api-key") {
        // Shopify OAuth start
        get("/v1/migration/shopify/oauth/start") {
            val shop = call.request.queryParameters["shop"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing shop parameter")
            val state = java.util.UUID.randomUUID().toString()
            val authUrl = "https://$shop/admin/oauth/authorize?" +
                "client_id=${config.shopifyClientId}&" +
                "scope=${config.shopifyScopes}&" +
                "redirect_uri=${config.shopifyRedirectUri}&" +
                "state=$state"
            call.respond(OAuthStartResponse(authUrl, state))
        }

        // Shopify OAuth callback (called by Shopify, not the POS)
        get("/v1/migration/shopify/oauth/callback") {
            val code = call.request.queryParameters["code"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing code")
            val shop = call.request.queryParameters["shop"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing shop")
            val state = call.request.queryParameters["state"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing state")
            call.respond(
                HttpStatusCode.NotImplemented,
                "Shopify OAuth exchange is not enabled: token vault and state validation are required before connecting $shop"
            )
        }

        // Square OAuth start
        get("/v1/migration/square/oauth/start") {
            val state = java.util.UUID.randomUUID().toString()
            val authUrl = "https://connect.squareup.com/oauth2/authorize?" +
                "client_id=${config.squareApplicationId}&" +
                "scope=CATALOG_READ+CUSTOMERS_READ+PAYMENTS_READ+ORDERS_READ&" +
                "state=$state"
            call.respond(OAuthStartResponse(authUrl, state))
        }

        // Square OAuth callback
        get("/v1/migration/square/oauth/callback") {
            val code = call.request.queryParameters["code"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing code")
            call.respond(
                HttpStatusCode.NotImplemented,
                "Square OAuth exchange is not enabled: token vault and state validation are required"
            )
        }

        // Start a migration job (dry-run or real)
        post("/v1/migration/jobs") {
            call.respond(
                HttpStatusCode.NotImplemented,
                "Migration worker is not configured; refusing to create a fake import job"
            )
        }

        // Poll migration job status
        get("/v1/migration/jobs/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(
                HttpStatusCode.NotImplemented,
                "Migration job persistence is not configured for job $id"
            )
        }

        // Resolve a migration conflict
        post("/v1/migration/jobs/{id}/conflicts/{conflictId}/resolve") {
            call.respond(
                HttpStatusCode.NotImplemented,
                "Migration conflict resolution is not configured"
            )
        }
    }
}
