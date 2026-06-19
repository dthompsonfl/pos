package com.enterprise.pos.backend.routes

import com.enterprise.pos.backend.stripe.StripeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionTokenRequest(val locationId: String? = null)

@Serializable
data class ConnectionTokenResponse(val secret: String)

/** POST /v1/terminal/connection-token — issue a short-lived connection token for the POS. */
fun Route.terminalConnectionTokenRoute() {
    authenticate("pos-api-key") {
        post("/v1/terminal/connection-token") {
            val req = call.receiveNullable<ConnectionTokenRequest>() ?: ConnectionTokenRequest()
            val secret = StripeService.createConnectionToken(req.locationId)
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
            require(req.amountMinor > 0) { "amountMinor must be > 0" }
            val intent = StripeService.createPaymentIntent(
                amountMinor = req.amountMinor,
                currency = req.currency,
                description = req.description,
                metadata = req.metadata + ("client_idempotency_key" to (req.idempotencyKey ?: ""))
            )
            call.respond(CreatePaymentIntentResponse(
                id = intent.id,
                clientSecret = intent.clientSecret,
                amount = intent.amount,
                currency = intent.currency
            ))
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
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val req = call.receiveNullable<CaptureRequest>() ?: CaptureRequest()
            val intent = StripeService.capturePaymentIntent(id, req.tipAmountMinor)
            call.respond(CaptureResponse(
                id = intent.id,
                status = intent.status,
                amount = intent.amount,
                amountCapturable = intent.amountCapturable
            ))
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
            val refund = StripeService.refund(
                chargeIdOrPaymentIntentId = req.paymentIntentId,
                amountMinor = req.amountMinor,
                reason = req.reason
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
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val intent = StripeService.retrievePaymentIntent(id)
            call.respond(PaymentLookupResponse(
                id = intent.id,
                status = intent.status,
                amount = intent.amount,
                amountCaptured = intent.amountCapturable,
                currency = intent.currency,
                chargeId = intent.latestChargeObject?.id
            ))
        }
    }
}
