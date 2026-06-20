package com.enterprise.pos.payment.stripe

import com.enterprise.pos.core.Logger
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.models.Reader

/**
 * Stripe Terminal [DiscoveryListener] that collects discovered readers into an internal list
 * and forwards them to the provider. Discovery runs asynchronously; the provider polls
 * [_discoveredReaders] after a fixed discovery window.
 */
class StripeTerminalDiscoveryDelegate(
    private val onReadersFound: (List<Reader>) -> Unit,
    private val onError: (com.stripe.stripeterminal.external.models.TerminalException) -> Unit,
    private val logger: Logger
) : DiscoveryListener {

    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
        logger.i(TAG, "Discovered ${readers.size} reader(s)")
        for (reader in readers) {
            logger.d(
                TAG,
                "Reader: id=${reader.id}, label=${reader.label}, " +
                    "serial=${reader.serialNumber}, deviceType=${reader.deviceType}, " +
                    "battery=${reader.batteryLevel}"
            )
        }
        onReadersFound(readers)
    }

    companion object {
        private const val TAG = "StripeDiscoveryDelegate"
    }
}
