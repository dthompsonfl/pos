package com.enterprise.pos.backend.routes

import com.enterprise.pos.backend.config.BackendConfig
import com.enterprise.pos.backend.stripe.StripeService
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.net.Webhook
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PaymentErrorResponse(val code: String, val message: String, val details: String? = null)

@Serializable
data class ConnectionTokenRequest(val locationId: String? = null, val idempotencyKey: String? = null)

@Serializable
data class ConnectionTokenResponse(val secret: String)

/** POST /v1/terminal/connection-token — issue a short-lived connection token for the POS. */
fun Route.terminalConnectionTokenRoute() {
    authenticate("pos-api-key") {
        post("/v1/terminal/connection-token") {
            val req = call.receiveNullable<ConnectionTokenRequest>() ?: ConnectionTokenRequest()
            val idempotencyKey = req.idempotencyKey ?: UUID.randomUUID().toString()
            val secret = StripeService.createConnectionToken(req.locationId, idempotencyKey = idempotencyKey)
            call.respond(ConnectionTokenResponse(secret))
        }
    }
}

@Serializable
data class CreatePaymentIntentRequest(
    val amountMinor: Long,
    val currency: String = "usd",
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null
)

@Serializable
data class CreatePaymentIntentResponse(
    val id: String,
    val clientSecret: String,
    val amount: Long,
    val currency: String
)

/** POST /v1/payments/payment-intents — create a PaymentIntent server-side. */
fun Route.paymentIntentsRoute() {
    authenticate("pos-api-key") {
        post("/v1/payments/payment-intents") {
            val req = call.receive<CreatePaymentIntentRequest>()

            if (req.amountMinor <= 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    PaymentErrorResponse("invalid_amount", "amountMinor must be > 0")
                )
            }
            if (req.currency.isBlank() || req.currency.length != 3) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    PaymentErrorResponse("invalid_currency", "currency must be a 3-letter ISO code")
                )
            }

            val idempotencyKey = req.idempotencyKey ?: UUID.randomUUID().toString()
            val intent = StripeService.createPaymentIntent(
                amountMinor = req.amountMinor,
                currency = req.currency,
                description = req.description,
                metadata = req.metadata + ("client_idempotency_key" to idempotencyKey),
                idempotencyKey = idempotencyKey
            )
            call.respond(
                CreatePaymentIntentResponse(
                    id = intent.id,
                    clientSecret = intent.clientSecret,
                    amount = intent.amount,
                    currency = intent.currency
                )
            )
        }
    }
}

@Serializable
data class CaptureRequest(val tipAmountMinor: Long? = null, val idempotencyKey: String? = null)

@Serializable
data class CaptureResponse(val id: String, val status: String, val amount: Long, val amountCapturable: Long)

/** POST /v1/payments/{id}/capture — capture an authorized PaymentIntent. */
fun Route.captureRoute() {
    authenticate("pos-api-key") {
        post("/v1/payments/{id}/capture") {
            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                PaymentErrorResponse("missing_id", "Payment intent ID is required")
            )
            if (!id.startsWith("pi_")) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    PaymentErrorResponse("invalid_id", "Payment intent ID must start with 'pi_'")
                )
            }

            val req = call.receiveNullable<CaptureRequest>() ?: CaptureRequest()
            val idempotencyKey = req.idempotencyKey ?: UUID.randomUUID().toString()
            val intent = StripeService.capturePaymentIntent(id, req.tipAmountMinor, idempotencyKey = idempotencyKey)
            call.respond(
                CaptureResponse(
                    id = intent.id,
                    status = intent.status,
                    amount = intent.amount,
                    amountCapturable = intent.amountCapturable
                )
            )
        }
    }
}

@Serializable
data class RefundRequest(
    val paymentIntentId: String,
    val amountMinor: Long? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

@Serializable
data class RefundResponse(val id: String, val status: String, val amount: Long)

/** POST /v1/refunds — refund a previously captured charge. */
fun Route.refundsRoute() {
    authenticate("pos-api-key") {
        post("/v1/refunds") {
            val req = call.receive<RefundRequest>()

            if (req.paymentIntentId.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    PaymentErrorResponse("missing_payment_intent_id", "paymentIntentId is required")
                )
            }
            if (req.amountMinor != null && req.amountMinor <= 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    PaymentErrorResponse("invalid_amount", "amountMinor must be > 0 if provided")
                )
            }

            val idempotencyKey = req.idempotencyKey ?: UUID.randomUUID().toString()
            val refund = StripeService.refund(
                chargeIdOrPaymentIntentId = req.paymentIntentId,
                amountMinor = req.amountMinor,
                reason = req.reason,
                idempotencyKey = idempotencyKey
            )
            call.respond(RefundResponse(refund.id, refund.status, refund.amount))
        }
    }
}

@Serializable
data class PaymentLookupResponse(
    val id: String,
    val status: String,
    val amount: Long,
    val amountCaptured: Long,
    val currency: String,
    val chargeId: String?
)

/** GET /v1/payments/{id} — look up a PaymentIntent's current status. */
fun Route.paymentLookupRoute() {
    authenticate("pos-api-key") {
        get("/v1/payments/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                PaymentErrorResponse("missing_id", "Payment intent ID is required")
            )
            val intent = StripeService.retrievePaymentIntent(id)
            call.respond(
                PaymentLookupResponse(
                    id = intent.id,
                    status = intent.status,
                    amount = intent.amount,
                    amountCaptured = intent.amountCapturable,
                    currency = intent.currency,
                    chargeId = intent.latestChargeObject?.id
                )
            )
        }
    }
}

// --- Webhook handling ---

private val processedWebhookEvents = ConcurrentHashMap<String, Long>()

fun verifyStripeSignature(payload: String, sigHeader: String, secret: String): Event {
    return Webhook.constructEvent(payload, sigHeader, secret)
}

/** POST /v1/webhooks/stripe — receive and process Stripe webhooks. */
fun Route.stripeWebhookRoute(config: BackendConfig) {
    post("/v1/webhooks/stripe") {
        if (config.stripeWebhookSecret.isBlank()) {
            return@post call.respond(
                HttpStatusCode.ServiceUnavailable,
                PaymentErrorResponse("webhook_not_configured", "Stripe webhook secret is not configured")
            )
        }

        val payload = call.receiveText()
        val sigHeader = call.request.headers["Stripe-Signature"]
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                PaymentErrorResponse("missing_signature", "Missing Stripe-Signature header")
            )

        val event = try {
            verifyStripeSignature(payload, sigHeader, config.stripeWebhookSecret)
        } catch (e: SignatureVerificationException) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                PaymentErrorResponse("invalid_signature", "Signature verification failed", e.message)
            )
        } catch (e: Exception) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                PaymentErrorResponse("signature_error", "Failed to verify webhook signature", e.message)
            )
        }

        // Deduplicate by event ID
        if (processedWebhookEvents.putIfAbsent(event.id, System.currentTimeMillis()) != null) {
            return@post call.respond(HttpStatusCode.OK) // already processed
        }

        // Persist event before processing (in-memory; replace with database in production)
        // Webhook event persistence to durable store is planned for v2.5.

        // Process based on event type
        when (event.type) {
            "payment_intent.succeeded" -> {
                // Internal payment status update, KDS notification, and sync outbox emission
                // are handled by the payment reconciliation worker.
            }
            "payment_intent.payment_failed" -> {
                // Payment failure handling and POS notification are handled by the
                // payment reconciliation worker.
            }
            "charge.refunded" -> {
                // Refund status update and POS notification are handled by the
                // payment reconciliation worker.
            }
            else -> {
                // Unhandled event type — log for observability
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}
