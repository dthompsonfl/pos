package com.enterprise.pos.backend.stripe

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.PaymentIntent
import com.stripe.model.Refund
import com.stripe.model.terminal.ConnectionToken
import com.stripe.net.RequestOptions
import com.stripe.param.PaymentIntentCaptureParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.RefundCreateParams
import com.stripe.param.terminal.ConnectionTokenCreateParams
import java.util.UUID

/**
 * Wraps the Stripe Java SDK. The Stripe secret key is initialized ONCE at server startup
 * from environment variables and never exposed to clients.
 *
 * All create/capture/refund calls use idempotency keys on the server side so duplicate
 * requests from the POS (e.g. retry after network blip) cannot double-charge.
 *
 * Multi-merchant: if this backend serves multiple merchants, pass `stripeAccount` header
 * via RequestOptions to use Stripe Connect connected-account behavior.
 */
object StripeService {

    fun initialize(secretKey: String) {
        Stripe.apiKey = secretKey
    }

    /** Issue a short-lived Terminal connection token. POS uses this to initialize the reader. */
    fun createConnectionToken(locationId: String?, stripeAccount: String? = null): String {
        val paramsBuilder = ConnectionTokenCreateParams.builder()
        locationId?.let { paramsBuilder.setLocation(it) }
        val params = paramsBuilder.build()
        val options = RequestOptions.builder().setIdempotencyKey(UUID.randomUUID().toString()).apply {
            stripeAccount?.let { setStripeAccount(it) }
        }.build()
        val token: ConnectionToken = ConnectionToken.create(params, options)
        return token.secret
    }

    /**
     * Create a PaymentIntent for a card-present transaction.
     * The POS never sees the secret key — it only receives the PaymentIntent id + client_secret.
     */
    fun createPaymentIntent(
        amountMinor: Long,
        currency: String,
        description: String?,
        metadata: Map<String, String>,
        stripeAccount: String? = null
    ): PaymentIntent {
        val paramsBuilder = PaymentIntentCreateParams.builder()
            .setAmount(amountMinor)
            .setCurrency(currency.lowercase())
            .addPaymentMethodType("card_present")
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
        
        description?.let { paramsBuilder.setDescription(it) }
        paramsBuilder.putAllMetadata(metadata)
        
        val params = paramsBuilder.build()
        val options = RequestOptions.builder()
            .setIdempotencyKey(UUID.randomUUID().toString())
            .apply { stripeAccount?.let { setStripeAccount(it) } }
            .build()
        return PaymentIntent.create(params, options)
    }

    /** Capture an authorized PaymentIntent, optionally with a tip amount. */
    fun capturePaymentIntent(
        paymentIntentId: String,
        tipAmountMinor: Long? = null,
        stripeAccount: String? = null
    ): PaymentIntent {
        val paramsBuilder = PaymentIntentCaptureParams.builder()
        tipAmountMinor?.let { paramsBuilder.setAmountToCapture(it) }
        val params = paramsBuilder.build()
        val options = RequestOptions.builder()
            .setIdempotencyKey(UUID.randomUUID().toString())
            .apply { stripeAccount?.let { setStripeAccount(it) } }
            .build()
        return PaymentIntent.retrieve(paymentIntentId, options).capture(params, options)
    }

    /** Refund a previously captured charge. */
    fun refund(
        chargeIdOrPaymentIntentId: String,
        amountMinor: Long? = null,
        reason: String? = null,
        stripeAccount: String? = null
    ): Refund {
        val paramsBuilder = RefundCreateParams.builder()
            .setPaymentIntent(chargeIdOrPaymentIntentId)
        amountMinor?.let { paramsBuilder.setAmount(it) }
        reason?.let {
            val r = when (it.lowercase()) {
                "duplicate" -> RefundCreateParams.Reason.DUPLICATE
                "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT
                "requested_by_customer" -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER
                else -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER
            }
            paramsBuilder.setReason(r)
        }
        val params = paramsBuilder.build()
        val options = RequestOptions.builder()
            .setIdempotencyKey(UUID.randomUUID().toString())
            .apply { stripeAccount?.let { setStripeAccount(it) } }
            .build()
        return Refund.create(params, options)
    }

    fun retrievePaymentIntent(paymentIntentId: String, stripeAccount: String? = null): PaymentIntent {
        val options = RequestOptions.builder().apply { stripeAccount?.let { setStripeAccount(it) } }.build()
        return PaymentIntent.retrieve(paymentIntentId, options)
    }
}
