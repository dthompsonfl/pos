package com.enterprise.pos.backend.routes

import com.enterprise.pos.backend.config.BackendConfig
import com.enterprise.pos.backend.storage.MigrationJob
import com.enterprise.pos.backend.storage.MigrationJobStore
import com.enterprise.pos.backend.storage.MigrationWorker
import com.enterprise.pos.backend.storage.ProviderToken
import com.enterprise.pos.backend.storage.TokenVault
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

// ---------------------------------------------------------------------------
// Request / Response DTOs
// ---------------------------------------------------------------------------

@Serializable
data class OAuthStartResponse(val authUrl: String, val state: String)

@Serializable
data class CreateMigrationJobRequest(
    val provider: String,
    val totalRecords: Int = 100
)

@Serializable
data class MigrationJobResponse(
    val id: String,
    val provider: String,
    val status: String,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val totalRecords: Int,
    val processedRecords: Int,
    val failedRecords: Int,
    val conflicts: List<MigrationConflictResponse> = emptyList(),
    val errorMessage: String? = null
)

@Serializable
data class MigrationConflictResponse(
    val id: String,
    val externalId: String,
    val reason: String,
    val resolution: String? = null
)

@Serializable
data class MigrationJobListResponse(
    val jobs: List<MigrationJobResponse>,
    val count: Int
)

@Serializable
data class ConflictResolveRequest(val resolution: String)

// ---------------------------------------------------------------------------
// Helper: map domain model to API response
// ---------------------------------------------------------------------------

private fun MigrationJob.toResponse(): MigrationJobResponse = MigrationJobResponse(
    id = id,
    provider = provider,
    status = status.name,
    createdAt = createdAt,
    startedAt = startedAt,
    completedAt = completedAt,
    totalRecords = totalRecords,
    processedRecords = processedRecords,
    failedRecords = failedRecords,
    conflicts = conflicts.map { conflict ->
        MigrationConflictResponse(
            id = conflict.id,
            externalId = conflict.externalId,
            reason = conflict.reason,
            resolution = conflict.resolution
        )
    },
    errorMessage = errorMessage
)

// ---------------------------------------------------------------------------
// Route wiring
// ---------------------------------------------------------------------------

/**
 * Migration routes.
 *
 * Rate limiting note: job creation should be limited to 5 requests per minute
 * per merchant to prevent accidental runaway imports. OAuth endpoints are
 * naturally bounded by the provider consent flow but state generation should
 * be rate-limited to prevent state-pool exhaustion.
 */
fun Route.migrationRoutes() {
    val config = BackendConfig.fromEnv()
    val jobStore = MigrationJobStore()
    val tokenVault = TokenVault()
    val worker = MigrationWorker(jobStore)
    val logger = LoggerFactory.getLogger("MigrationRoutes")

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        expectSuccess = false
    }

    // -----------------------------------------------------------------------
    // Shopify OAuth — start (called by POS)
    // -----------------------------------------------------------------------
    authenticate("pos-api-key") {
        get("/v1/migration/shopify/oauth/start") {
            val shop = call.request.queryParameters["shop"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing shop parameter")
            val state = java.util.UUID.randomUUID().toString()
            tokenVault.storeOAuthState(state, "shopify")
            val authUrl = "https://$shop/admin/oauth/authorize?" +
                "client_id=${config.shopifyClientId}&" +
                "scope=${config.shopifyScopes}&" +
                "redirect_uri=${config.shopifyRedirectUri}&" +
                "state=$state"
            call.respond(OAuthStartResponse(authUrl, state))
        }
    }

    // -----------------------------------------------------------------------
    // Shopify OAuth — callback (called by Shopify, NOT authenticated)
    // -----------------------------------------------------------------------
    get("/v1/migration/shopify/oauth/callback") {
        val code = call.request.queryParameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing code")
        val shop = call.request.queryParameters["shop"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing shop")
        val state = call.request.queryParameters["state"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing state")

        val provider = tokenVault.consumeOAuthState(state)
        if (provider != "shopify") {
            return@get call.respond(HttpStatusCode.BadRequest, "Invalid or expired OAuth state")
        }

        // Exchange code for access token
        try {
            val response = httpClient.post("https://$shop/admin/oauth/access_token") {
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "client_id" to config.shopifyClientId,
                        "client_secret" to config.shopifyClientSecret,
                        "code" to code
                    )
                )
            }

            if (response.status != HttpStatusCode.OK) {
                logger.error("Shopify token exchange failed for {}: {}", shop, response.status)
                return@get call.respond(
                    HttpStatusCode.BadGateway,
                    "Shopify token exchange failed: ${response.status}"
                )
            }

            val tokenBody: Map<String, String> = response.body()
            val accessToken = tokenBody["access_token"]
                ?: return@get call.respond(
                    HttpStatusCode.BadGateway,
                    "Shopify response did not contain access_token"
                )
            val scope = tokenBody["scope"] ?: config.shopifyScopes

            tokenVault.storeToken(
                ProviderToken(
                    provider = "shopify",
                    merchantId = shop,
                    accessToken = accessToken,
                    refreshToken = null, // Shopify offline tokens do not expire
                    expiresAt = null,
                    scope = scope
                )
            )

            call.respondText(
                buildHtmlSuccessPage("Shopify", shop),
                ContentType.Text.Html
            )
        } catch (ex: Exception) {
            logger.error("Shopify OAuth callback exception for {}", shop, ex)
            call.respond(HttpStatusCode.InternalServerError, "OAuth callback failed: ${ex.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Square OAuth — start (called by POS)
    // -----------------------------------------------------------------------
    authenticate("pos-api-key") {
        get("/v1/migration/square/oauth/start") {
            val state = java.util.UUID.randomUUID().toString()
            tokenVault.storeOAuthState(state, "square")
            val authUrl = "${config.squareAuthorizeUrl}?" +
                "client_id=${config.squareApplicationId}&" +
                "scope=CATALOG_READ+CUSTOMERS_READ+PAYMENTS_READ+ORDERS_READ&" +
                "redirect_uri=${config.squareRedirectUri}&" +
                "state=$state"
            call.respond(OAuthStartResponse(authUrl, state))
        }
    }

    // -----------------------------------------------------------------------
    // Square OAuth — callback (called by Square, NOT authenticated)
    // -----------------------------------------------------------------------
    get("/v1/migration/square/oauth/callback") {
        val code = call.request.queryParameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing code")
        val state = call.request.queryParameters["state"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing state")

        val provider = tokenVault.consumeOAuthState(state)
        if (provider != "square") {
            return@get call.respond(HttpStatusCode.BadRequest, "Invalid or expired OAuth state")
        }

        try {
            val response = httpClient.post(config.squareTokenUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "client_id" to config.squareApplicationId,
                        "client_secret" to config.squareApplicationSecret,
                        "code" to code,
                        "grant_type" to "authorization_code",
                        "redirect_uri" to config.squareRedirectUri
                    )
                )
            }

            if (response.status != HttpStatusCode.OK) {
                logger.error("Square token exchange failed: {}", response.status)
                return@get call.respond(
                    HttpStatusCode.BadGateway,
                    "Square token exchange failed: ${response.status}"
                )
            }

            val tokenBody: Map<String, String> = response.body()
            val accessToken = tokenBody["access_token"]
                ?: return@get call.respond(
                    HttpStatusCode.BadGateway,
                    "Square response did not contain access_token"
                )
            val refreshToken = tokenBody["refresh_token"]
            val merchantId = tokenBody["merchant_id"] ?: "unknown"
            val scope = tokenBody["scope"] ?: "CATALOG_READ+CUSTOMERS_READ+PAYMENTS_READ+ORDERS_READ"
            val expiresAt = tokenBody["expires_at"]?.toLongOrNull()
                ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) // 30 days default

            tokenVault.storeToken(
                ProviderToken(
                    provider = "square",
                    merchantId = merchantId,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    scope = scope
                )
            )

            call.respondText(
                buildHtmlSuccessPage("Square", merchantId),
                ContentType.Text.Html
            )
        } catch (ex: Exception) {
            logger.error("Square OAuth callback exception", ex)
            call.respond(HttpStatusCode.InternalServerError, "OAuth callback failed: ${ex.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Job management (all authenticated)
    // -----------------------------------------------------------------------
    authenticate("pos-api-key") {

        // POST /v1/migration/jobs — create a migration job
        post("/v1/migration/jobs") {
            val req = call.receive<CreateMigrationJobRequest>()
            if (req.provider !in setOf("shopify", "square")) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MigrationJobResponse(
                        id = "",
                        provider = req.provider,
                        status = "REJECTED",
                        createdAt = 0,
                        totalRecords = 0,
                        processedRecords = 0,
                        failedRecords = 0,
                        errorMessage = "Unsupported provider: ${req.provider}. Use shopify or square."
                    )
                )
            }

            val totalRecords = req.totalRecords.coerceIn(1, 10_000)
            val job = jobStore.createJob(req.provider, totalRecords)
            worker.startJob(job.id)

            call.respond(HttpStatusCode.Created, job.toResponse())
        }

        // GET /v1/migration/jobs — list all jobs
        get("/v1/migration/jobs") {
            val jobs = jobStore.listJobs().map { it.toResponse() }
            call.respond(MigrationJobListResponse(jobs = jobs, count = jobs.size))
        }

        // GET /v1/migration/jobs/{id} — get job status
        get("/v1/migration/jobs/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing job id")

            val job = jobStore.getJob(id)
            if (job != null) {
                call.respond(job.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound, "Job $id not found")
            }
        }

        // DELETE /v1/migration/jobs/{id} — cancel a job
        delete("/v1/migration/jobs/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing job id")

            val result = jobStore.cancelJob(id)
            result.fold(
                onSuccess = { cancelledJob ->
                    call.respond(cancelledJob.toResponse())
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Cannot cancel job: ${error.message}"
                    )
                }
            )
        }

        // POST /v1/migration/jobs/{id}/conflicts/{conflictId}/resolve — resolve conflict
        post("/v1/migration/jobs/{id}/conflicts/{conflictId}/resolve") {
            val jobId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing job id")
            val conflictId = call.parameters["conflictId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing conflict id")
            val req = call.receive<ConflictResolveRequest>()

            val result = jobStore.resolveConflict(jobId, conflictId, req.resolution)
            result.fold(
                onSuccess = { updatedJob ->
                    call.respond(updatedJob.toResponse())
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Resolution failed: ${error.message}"
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// HTML success page for OAuth callbacks
// ---------------------------------------------------------------------------

private fun buildHtmlSuccessPage(provider: String, merchantId: String): String = """
<!DOCTYPE html>
<html>
<head><title>Enterprise POS — OAuth Connected</title></head>
<body style="font-family:sans-serif;text-align:center;padding-top:4rem">
    <h1 style="color:#2e7d32">Connected successfully</h1>
    <p>Your <strong>$provider</strong> store <strong>$merchantId</strong> is now linked to Enterprise POS.</p>
    <p>You can close this window and return to the app.</p>
</body>
</html>
""".trimIndent()
