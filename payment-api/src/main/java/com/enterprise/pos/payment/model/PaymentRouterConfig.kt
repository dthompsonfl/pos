package com.enterprise.pos.payment.model

import com.enterprise.pos.core.Money
import kotlinx.serialization.Serializable

/** Configuration for the payment router. Injected at app startup. */
@Serializable
data class PaymentRouterConfig(
    val enabledProviders: Set<PaymentProviderId>,
    val defaultProvider: PaymentProviderId,
    val fallbackProvider: PaymentProviderId? = null,
    val enableOfflineMode: Boolean = true,
    val maxOfflineAmount: Money = Money.of(500.0),
    val tipSuggestions: List<TipSuggestion> = listOf(
        TipSuggestion.Percentage(15),
        TipSuggestion.Percentage(18),
        TipSuggestion.Percentage(20),
        TipSuggestion.Percentage(25),
        TipSuggestion.Custom
    )
)

@Serializable
sealed class TipSuggestion {
    @Serializable
    data class Percentage(val percent: Int) : TipSuggestion()
    @Serializable
    data class Fixed(val amount: Money) : TipSuggestion()
    @Serializable
    data object Custom : TipSuggestion()
    @Serializable
    data object NoTip : TipSuggestion()
}

/** Routing decision returned by the router's policy engine. */
data class RoutingDecision(
    val provider: PaymentProviderId,
    val reason: String,
    val allowsOffline: Boolean
)

/** Policy that decides which provider to use for a given payment. */
interface PaymentRoutingPolicy {
    fun decide(
        amount: Money,
        requestedProvider: PaymentProviderId?,
        availableProviders: Set<PaymentProviderId>,
        connectedReaders: Map<PaymentProviderId, Boolean>,
        config: PaymentRouterConfig
    ): RoutingDecision
}

class DefaultRoutingPolicy : PaymentRoutingPolicy {
    override fun decide(
        amount: Money,
        requestedProvider: PaymentProviderId?,
        availableProviders: Set<PaymentProviderId>,
        connectedReaders: Map<PaymentProviderId, Boolean>,
        config: PaymentRouterConfig
    ): RoutingDecision {
        // 1. Honor explicit request if available.
        requestedProvider?.let { req ->
            if (req in availableProviders) {
                val allowsOffline = config.enableOfflineMode && amount <= config.maxOfflineAmount
                return RoutingDecision(req, "Requested by user", allowsOffline)
            }
        }

        // 2. Prefer the default if available and connected.
        if (config.defaultProvider in availableProviders && connectedReaders[config.defaultProvider] == true) {
            val allowsOffline = config.enableOfflineMode && amount <= config.maxOfflineAmount
            return RoutingDecision(config.defaultProvider, "Default provider (connected)", allowsOffline)
        }

        // 3. Pick any connected provider.
        val connected = connectedReaders.entries.firstOrNull { it.value && it.key in availableProviders }
        if (connected != null) {
            return RoutingDecision(connected.key, "Auto-selected (reader connected)", false)
        }

        // 4. Fallback to manual / cash if enabled.
        if (PaymentProviderId.CASH in availableProviders) {
            return RoutingDecision(PaymentProviderId.CASH, "Fallback to cash", false)
        }
        if (PaymentProviderId.MANUAL in availableProviders) {
            return RoutingDecision(PaymentProviderId.MANUAL, "Fallback to manual entry", false)
        }

        // 5. Last resort: the configured default even if no reader.
        return RoutingDecision(config.defaultProvider, "Default provider (no reader connected)", false)
    }
}
