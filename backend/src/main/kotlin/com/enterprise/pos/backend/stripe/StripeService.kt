package com.enterprise.pos.backend.stripe

import com.enterprise.pos.backend.storage.IdempotencyRecord
import com.enterprise.pos.backend.storage.IdempotencyStore
import com.google.gson.Gson
import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.model.Refund
import com.stripe.model.terminal.ConnectionToken
import com.stripe.net.RequestOptions
import com.stripe.param.PaymentIntentCaptureParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.RefundCreateParams
import com.stripe.param.terminal.ConnectionTokenCreateParams
import java.security.MessageDigest
import java.util.Base64

/**
 * Wraps the Stripe Java SDK. The Stripe secret key is initialized ONCE at server startup
 * from environment variables and never exposed to clients.
 *
 * All create/capture/refund calls accept idempotency keys from the caller so duplicate
 * requests from the POS (e.g. retry after network blip) cannot double-charge.
 * The service maintains an in-memory cache of idempotency records keyed by the caller's
 * idempotency key. If the key is reused with different parameters, an error is thrown.
 *
 * Multi-merchant: if this backend serves multiple merchants, pass `stripeAccount` header
 * via RequestOptions to use Stripe Connect connected-account behavior.
 */
object StripeService {
    private val gson = Gson()
    private val idempotencyStore = IdempotencyStore()

    fun initialize(secretKey: String) {
        Stripe.apiKey = secretKey
    }

    private fun computeParamsHash(vararg parts: Any?): String {
        val input = parts.joinToString("|") { it?.toString() ?: "null" }
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(input.toByteArray()))
    }

    private fun checkIdempotency(key: String, operation: String, paramsHash: String): IdempotencyRecord? {
        val existing = idempotencyStore.get(key)
        return when {
            existing == null -> null
            existing.operation != operation -> throw IllegalArgumentException(
                "Idempotency key reused for different operation: expected $operation, found ${existing.operation}"
            )
            existing.paramsHash != paramsHash -> throw IllegalArgumentException(
                "Idempotency key reused with different parameters"
            )
            else -> existing
        }
    }

    /** Issue a short-lived Terminal connection token. POS uses this to initialize the reader. */
    fun createConnectionToken(locationId: String?, stripeAccount: String? = null, idempotencyKey: String): String {
        val paramsHash = computeParamsHash(locationId, stripeAccount)
        val cached = checkIdempotency(idempotencyKey, "create-connection-token", paramsHash)
        if (cached != null) {
            return gson.fromJson(cached.responseJson, ConnectionToken::class.java).secret
        }

        val paramsBuilder = ConnectionTokenCreateParams.builder()
        locationId?.let { paramsBuilder.setLocation(it) }
        val params = paramsBuilder.build()
        val options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).apply {
            stripeAccount?.let { setStripeAccount(it) }
        }.build()
        val token: ConnectionToken = ConnectionToken.create(params, options)
        val json = gson.toJson(token)
        idempotencyStore.put(IdempotencyRecord(idempotencyKey, "create-connection-token", paramsHash, json))
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
        stripeAccount: String? = null,
        idempotencyKey: String
    ): PaymentIntent {
        val paramsHash = computeParamsHash(amountMinor, currency, description, metadata, stripeAccount)
        val cached = checkIdempotency(idempotencyKey, "create-payment-intent", paramsHash)
        if (cached != null) {
            return gson.fromJson(cached.responseJson, PaymentIntent::class.java)
        }

        val paramsBuilder = PaymentIntentCreateParams.builder()
            .setAmount(amountMinor)
            .setCurrency(currency.lowercase())
            .addPaymentMethodType("card_present")
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)

        description?.let { paramsBuilder.setDescription(it) }
        paramsBuilder.putAllMetadata(metadata)

        val params = paramsBuilder.build()
        val options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .apply { stripeAccount?.let { setStripeAccount(it) } }
            .build()
        val result = PaymentIntent.create(params, options)
        val json = gson.toJson(result)
        idempotencyStore.put(IdempotencyRecord(idempotencyKey, "create-payment-intent", paramsHash, json))
        return result
    }

    /** Capture an authorized PaymentIntent, optionally with a tip amount. */
    fun capturePaymentIntent(
        paymentIntentId: String,
        tipAmountMinor: Long? = null,
        stripeAccount: String? = null,
        idempotencyKey: String
    ): PaymentIntent {
        val paramsHash = computeParamsHash(paymentIntentId, tipAmountMinor, stripeAccount)
        val cached = checkIdempotency(idempotencyKey, "capture-payment-intent", paramsHash)
        if (cached != null) {
            return gson.fromJson(cached.responseJson, PaymentIntent::class.java)
        }

        val paramsBuilder = PaymentIntentCaptureParams.builder()
        tipAmountMinor?.let { paramsBuilder.setAmountToCapture(it) }
        val params = paramsBuilder.build()
        val options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .apply { stripeAccount?.let { setStripeAccount(it) } }
            .build()
        val result = PaymentIntent.retrieve(paymentIntentId, options).capture(params, options)
        val json = gson.toJson(result)
        idempotencyStore.put(IdempotencyRecord(idempotencyKey, "capture-payment-intent", paramsHash, json))
        return result
    }

    /** Refund a previously captured charge. */
    fun refund(
        chargeIdOrPaymentIntentId: String,
        amountMinor: Long? = null,
        reason: String? = null,
        stripeAccount: String? = null,
        idempotencyKey: String
    ): Refund {
        val paramsHash = computeParamsHash(chargeIdOrPaymentIntentId, amountMinor, reason, stripeAccount)
        val cached = checkIdempotency(idempotencyKey, "refund", paramsHash)
        if (cached != null) {
            return gson.fromJson(cached.responseJson, Refund::class.java)
        }

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
            .setIdempotencyKey(idempotencyKey)
            .apply { stripeAccount?.let { setStripeAccount(it) } }
            .build()
        val result = Refund.create(params, options)
        val json = gson.toJson(result)
        idempotencyStore.put(IdempotencyRecord(idempotencyKey, "refund", paramsHash, json))
        return result
    }

    fun retrievePaymentIntent(paymentIntentId: String, stripeAccount: String? = null): PaymentIntent {
        val options = RequestOptions.builder().apply { stripeAccount?.let { setStripeAccount(it) } }.build()
        return PaymentIntent.retrieve(paymentIntentId, options)
    }
}
