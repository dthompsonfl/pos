package com.enterprise.pos.payment.router

import com.enterprise.pos.core.Money
import com.enterprise.pos.payment.model.DefaultRoutingPolicy
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentRouterConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultRoutingPolicyTest {

    private val policy = DefaultRoutingPolicy()
    private val config = PaymentRouterConfig(
        enabledProviders = setOf(
            PaymentProviderId.STRIPE,
            PaymentProviderId.SQUARE,
            PaymentProviderId.SHOPIFY,
            PaymentProviderId.CASH,
            PaymentProviderId.MANUAL
        ),
        defaultProvider = PaymentProviderId.STRIPE,
        fallbackProvider = PaymentProviderId.CASH
    )

    @Test
    fun `explicit provider request is honored when available`() {
        val d = policy.decide(
            amount = Money.of(50.0),
            requestedProvider = PaymentProviderId.SQUARE,
            availableProviders = config.enabledProviders,
            connectedReaders = mapOf(PaymentProviderId.STRIPE to true),
            config = config
        )
        assertThat(d.provider).isEqualTo(PaymentProviderId.SQUARE)
    }

    @Test
    fun `default provider is selected when connected`() {
        val d = policy.decide(
            amount = Money.of(50.0),
            requestedProvider = null,
            availableProviders = config.enabledProviders,
            connectedReaders = mapOf(PaymentProviderId.STRIPE to true),
            config = config
        )
        assertThat(d.provider).isEqualTo(PaymentProviderId.STRIPE)
    }

    @Test
    fun `falls back to first connected provider when default not connected`() {
        val d = policy.decide(
            amount = Money.of(50.0),
            requestedProvider = null,
            availableProviders = config.enabledProviders,
            connectedReaders = mapOf(
                PaymentProviderId.STRIPE to false,
                PaymentProviderId.SQUARE to true
            ),
            config = config
        )
        assertThat(d.provider).isEqualTo(PaymentProviderId.SQUARE)
    }

    @Test
    fun `falls back to cash when no readers connected`() {
        val d = policy.decide(
            amount = Money.of(50.0),
            requestedProvider = null,
            availableProviders = config.enabledProviders,
            connectedReaders = emptyMap(),
            config = config
        )
        assertThat(d.provider).isEqualTo(PaymentProviderId.CASH)
    }

    @Test
    fun `offline allowed only when amount under threshold`() {
        val d = policy.decide(
            amount = Money.of(1000.0), // > 500 default
            requestedProvider = PaymentProviderId.STRIPE,
            availableProviders = config.enabledProviders,
            connectedReaders = mapOf(PaymentProviderId.STRIPE to true),
            config = config
        )
        assertThat(d.allowsOffline).isFalse()
    }

    @Test
    fun `offline allowed when amount under threshold`() {
        val d = policy.decide(
            amount = Money.of(100.0),
            requestedProvider = PaymentProviderId.STRIPE,
            availableProviders = config.enabledProviders,
            connectedReaders = mapOf(PaymentProviderId.STRIPE to true),
            config = config
        )
        assertThat(d.allowsOffline).isTrue()
    }
}
