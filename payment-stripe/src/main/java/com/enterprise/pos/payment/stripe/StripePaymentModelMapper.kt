package com.enterprise.pos.payment.stripe

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.PaymentErrorCode
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.payment.model.ConnectionType
import com.enterprise.pos.payment.model.DiscoveredReader
import com.enterprise.pos.payment.model.EntryMode
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentResult
import com.enterprise.pos.payment.model.RefundResult
import com.enterprise.pos.payment.model.RefundStatus
import com.stripe.stripeterminal.external.models.DeviceType
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.Refund
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.external.models.TerminalException.TerminalErrorCode

/**
 * Maps Stripe Terminal SDK objects to POS domain objects and vice versa.
 * All mapping is deterministic and null-safe.
 */
object StripePaymentModelMapper {

    fun mapReader(reader: Reader): DiscoveredReader {
        return DiscoveredReader(
            id = reader.id,
            displayName = reader.label?.takeIf { it.isNotBlank() }
                ?: reader.serialNumber?.takeIf { it.isNotBlank() }
                ?: "Unknown Reader",
            model = reader.deviceType?.name?.lowercase()?.replace("_", "-") ?: "unknown",
            batteryLevel = reader.batteryLevel?.takeIf { it >= 0f },
            connectionType = mapConnectionType(reader.deviceType),
            serial = reader.serialNumber
        )
    }

    fun mapPaymentIntentToResult(
        paymentIntent: PaymentIntent,
        provider: PaymentProviderId,
        paymentId: PaymentId
    ): PaymentResult {
        val charge = paymentIntent.charges?.firstOrNull()
        val cardDetails = charge?.paymentMethodDetails?.cardPresent
            ?: charge?.paymentMethodDetails?.card

        return PaymentResult(
            id = paymentId,
            provider = provider,
            providerTransactionId = paymentIntent.id,
            amount = Money.ofMinor(paymentIntent.amount),
            currency = paymentIntent.currency.uppercase(),
            cardBrand = cardDetails?.brand,
            last4 = cardDetails?.last4,
            entryMode = mapEntryMode(cardDetails?.readMethod),
            receiptUrl = charge?.receiptUrl,
            capturedAt = System.currentTimeMillis(),
            metadata = buildMap {
                put("stripe_status", paymentIntent.status)
                cardDetails?.network?.let { put("card_network", it) }
                cardDetails?.receiptDetails?.let {
                    put("authorization_code", it.authorizationCode ?: "")
                }
            }
        )
    }

    fun mapRefundToResult(refund: Refund, originalPaymentId: PaymentId): RefundResult {
        return RefundResult(
            id = refund.id,
            originalPaymentId = originalPaymentId,
            amount = Money.ofMinor(refund.amount),
            status = when (refund.status) {
                "succeeded" -> RefundStatus.SUCCEEDED
                "pending" -> RefundStatus.PENDING
                "failed" -> RefundStatus.FAILED
                else -> RefundStatus.FAILED
            },
            providerRefundId = refund.id,
            createdAt = System.currentTimeMillis()
        )
    }

    fun mapTerminalException(error: TerminalException): PaymentErrorCode {
        return when (error.errorCode) {
            TerminalErrorCode.CANCELLED -> PaymentErrorCode.CANCELLED
            TerminalErrorCode.PAYMENT_DECLINED -> PaymentErrorCode.DECLINED
            TerminalErrorCode.CARD_READ_TIMED_OUT -> PaymentErrorCode.CARD_READ_FAILED
            TerminalErrorCode.PAYMENT_INTENT_PAYMENT_FAILED -> PaymentErrorCode.CARD_READ_FAILED
            TerminalErrorCode.PAYMENT_INTENT_UNEXPECTED_STATUS -> PaymentErrorCode.CARD_READ_FAILED
            TerminalErrorCode.READER_NOT_CONNECTED,
            TerminalErrorCode.READER_CONNECTION_DENIED -> PaymentErrorCode.READER_DISCONNECTED
            TerminalErrorCode.READER_SOFTWARE_UPDATE_FAILED -> PaymentErrorCode.READER_DISCONNECTED
            TerminalErrorCode.API_CONNECTION_ERROR,
            TerminalErrorCode.NETWORK_ERROR,
            TerminalErrorCode.INTERNET_NOT_REACHABLE -> PaymentErrorCode.NETWORK_ERROR
            TerminalErrorCode.PAYMENT_INTENT_AMOUNTS_MISMATCH -> PaymentErrorCode.AMOUNT_MISMATCH
            TerminalErrorCode.REFUND_FAILED -> PaymentErrorCode.REFUND_FAILED
            else -> PaymentErrorCode.UNKNOWN
        }
    }

    private fun mapConnectionType(deviceType: DeviceType?): ConnectionType {
        return when (deviceType) {
            DeviceType.VERIFONE_P400,
            DeviceType.VERIFONE_P400_PLUS -> ConnectionType.WIFI
            DeviceType.WISEPOS_E,
            DeviceType.STRIPE_M2,
            DeviceType.CHIPPER_2X,
            DeviceType.WISEPAD_3,
            DeviceType.CHIPPER_2X_BT -> ConnectionType.BLUETOOTH
            DeviceType.STRIPE_S700 -> ConnectionType.WIFI
            else -> ConnectionType.BLUETOOTH
        }
    }

    private fun mapEntryMode(readMethod: String?): EntryMode {
        return when (readMethod?.lowercase()) {
            "contactless" -> EntryMode.CONTACTLESS
            "contact", "chip" -> EntryMode.CHIP
            "swipe" -> EntryMode.SWIPE
            "manual" -> EntryMode.MANUAL
            else -> EntryMode.CHIP
        }
    }
}
