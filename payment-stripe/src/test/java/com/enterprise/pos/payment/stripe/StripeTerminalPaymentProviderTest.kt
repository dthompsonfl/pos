package com.enterprise.pos.payment.stripe

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.Result
import com.enterprise.pos.payment.model.ConnectionType
import com.enterprise.pos.payment.model.CreatePaymentRequest
import com.enterprise.pos.payment.model.DiscoveredReader
import com.enterprise.pos.payment.model.EntryMode
import com.enterprise.pos.payment.model.PaymentIntentHandle
import com.enterprise.pos.payment.model.ProviderConfig
import com.enterprise.pos.payment.model.ProviderEnvironment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class StripeTerminalPaymentProviderTest {

    /** Simple test [AuthTokenProvider] that returns a fixed token. */
    private class TestAuthTokenProvider(private val token: String) : com.enterprise.pos.core.security.AuthTokenProvider {
        override fun getToken(): String? = token
        override suspend fun refreshToken(): com.enterprise.pos.core.Result<String> =
            com.enterprise.pos.core.Result.success(token)
        override fun clearToken() {}
    }

    private fun provider(simulate: Boolean = true) = StripeTerminalPaymentProvider(
        context = android.app.Application(),
        backendBaseUrl = "https://test.example.com",
        connectionTokenEndpoint = "/v1/tokens",
        authTokenProvider = TestAuthTokenProvider("test-token"),
        simulate = simulate
    )

    @Test
    fun `initialize in simulated mode sets connected status`() = runBlocking {
        val provider = provider(simulate = true)
        val result = provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `discoverReaders in simulated mode returns simulated readers`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val result = provider.discoverReaders()
        assertThat(result.isSuccess()).isTrue()
        val readers = result.getOrThrow()
        assertThat(readers).isNotEmpty()
        assertThat(readers[0].displayName).isEqualTo("Simulated BBPOS WisePOS E")
    }

    @Test
    fun `connectReader in simulated mode succeeds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val reader = DiscoveredReader(
            id = "sim-reader-1",
            displayName = "Simulated Reader",
            model = "bbpos-wisepos-e",
            connectionType = ConnectionType.BLUETOOTH,
            serial = "SIM-001"
        )
        val result = provider.connectReader(reader)
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `disconnectReader in simulated mode succeeds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val reader = DiscoveredReader(
            id = "sim-reader-1",
            displayName = "Simulated Reader",
            model = "bbpos-wisepos-e",
            connectionType = ConnectionType.BLUETOOTH,
            serial = "SIM-001"
        )
        provider.connectReader(reader)
        val result = provider.disconnectReader()
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `createPaymentIntent in simulated mode returns handle`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val request = CreatePaymentRequest(
            orderId = com.enterprise.pos.core.OrderId("order-1"),
            amount = Money.of(25.00),
            currency = "USD"
        )
        val result = provider.createPaymentIntent(request)
        assertThat(result.isSuccess()).isTrue()
        val handle = result.getOrThrow()
        assertThat(handle.amount).isEqualTo(Money.of(25.00))
        assertThat(handle.provider.name).isEqualTo("STRIPE")
    }

    @Test
    fun `collectPayment in simulated mode succeeds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val request = CreatePaymentRequest(
            orderId = com.enterprise.pos.core.OrderId("order-1"),
            amount = Money.of(25.00),
            currency = "USD"
        )
        val handle = provider.createPaymentIntent(request).getOrThrow()
        val events = mutableListOf<com.enterprise.pos.payment.model.PaymentEvent>()
        val result = provider.collectPayment(handle) { event -> events.add(event) }
        assertThat(result.isSuccess()).isTrue()
        val paymentResult = result.getOrThrow()
        assertThat(paymentResult.amount).isEqualTo(Money.of(25.00))
        assertThat(paymentResult.cardBrand).isNull()
        assertThat(paymentResult.last4).isNull()
        assertThat(paymentResult.entryMode).isEqualTo(EntryMode.CHIP)
    }

    @Test
    fun `cancelPayment in simulated mode succeeds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val handle = PaymentIntentHandle(
            provider = com.enterprise.pos.payment.model.PaymentProviderId.STRIPE,
            intentId = "pi_sim_test",
            secret = "secret",
            amount = Money.of(25.00),
            currency = "USD",
            createdAt = System.currentTimeMillis()
        )
        val result = provider.cancelPayment(handle)
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `refund in simulated mode succeeds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val result = provider.refund(PaymentId("pi_test"), Money.of(10.00), "Customer request")
        assertThat(result.isSuccess()).isTrue()
        val refundResult = result.getOrThrow()
        assertThat(refundResult.status).isEqualTo(com.enterprise.pos.payment.model.RefundStatus.SUCCEEDED)
        assertThat(refundResult.amount).isEqualTo(Money.of(10.00))
    }

    @Test
    fun `refund zero amount returns failed status`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val result = provider.refund(PaymentId("pi_test"), Money.ZERO, "Mistake")
        assertThat(result.isSuccess()).isTrue()
        val refundResult = result.getOrThrow()
        assertThat(refundResult.status).isEqualTo(com.enterprise.pos.payment.model.RefundStatus.FAILED)
    }

    @Test
    fun `close in simulated mode succeeds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val result = provider.close()
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `retryOfflineRefunds returns zero when no queued refunds`() = runBlocking {
        val provider = provider(simulate = true)
        provider.initialize(ProviderConfig(environment = ProviderEnvironment.SANDBOX))
        val result = provider.retryOfflineRefunds()
        assertThat(result.isSuccess()).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(0)
    }
}
