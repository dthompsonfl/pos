package com.enterprise.pos.payment.stripe

import com.enterprise.pos.core.Logger
import com.enterprise.pos.payment.model.PaymentEvent
import com.enterprise.pos.payment.model.ReaderStatus
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.BatteryStatus
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader

/**
 * Stripe Terminal [TerminalListener] that bridges SDK reader and payment lifecycle events
 * to the POS system. Tracks connection status, payment progress, battery levels, and
 * unexpected disconnects.
 */
class StripeTerminalReaderDelegate(
    private val onStatusChange: (ReaderStatus) -> Unit,
    private val onEvent: (PaymentEvent) -> Unit,
    private val logger: Logger
) : TerminalListener {

    private var currentReader: Reader? = null

    fun setCurrentReader(reader: Reader?) {
        currentReader = reader
    }

    override fun onConnectionStatusChange(status: ConnectionStatus) {
        logger.i(TAG, "Connection status changed: $status")
        when (status) {
            ConnectionStatus.NOT_CONNECTED -> {
                currentReader = null
                onStatusChange(ReaderStatus.NotConnected)
            }
            ConnectionStatus.CONNECTING -> {
                val readerId = currentReader?.id ?: "unknown"
                onStatusChange(ReaderStatus.Connecting(readerId))
            }
            ConnectionStatus.CONNECTED -> {
                val reader = currentReader
                if (reader != null) {
                    onStatusChange(
                        ReaderStatus.Connected(StripePaymentModelMapper.mapReader(reader))
                    )
                } else {
                    onStatusChange(ReaderStatus.Error("Connected but no reader reference"))
                }
            }
        }
    }

    override fun onPaymentStatusChange(status: PaymentStatus) {
        logger.i(TAG, "Payment status changed: $status")
        when (status) {
            PaymentStatus.WAITING_FOR_INPUT -> onEvent(PaymentEvent.InsertCard())
            PaymentStatus.PROCESSING -> onEvent(PaymentEvent.Processing())
            PaymentStatus.READY -> { /* idle */ }
            PaymentStatus.NOT_READY -> { /* idle */ }
        }
    }

    override fun onUnexpectedReaderDisconnect(reader: Reader) {
        logger.w(TAG, "Unexpected reader disconnect: ${reader.label}")
        currentReader = null
        onStatusChange(ReaderStatus.NotConnected)
        onEvent(PaymentEvent.Error("Reader ${reader.label} disconnected unexpectedly"))
    }

    override fun onBatteryLevelUpdate(
        batteryLevel: Float,
        isCharging: Boolean,
        batteryStatus: BatteryStatus
    ) {
        val levelPercent = (batteryLevel * 100).toInt()
        logger.i(
            TAG,
            "Battery level: $levelPercent%, charging: $isCharging, status: $batteryStatus"
        )
        if (batteryLevel <= 0.15f && !isCharging) {
            onEvent(PaymentEvent.ReaderMessage("Reader battery low ($levelPercent%)"))
        }
    }

    companion object {
        private const val TAG = "StripeReaderDelegate"
    }
}
