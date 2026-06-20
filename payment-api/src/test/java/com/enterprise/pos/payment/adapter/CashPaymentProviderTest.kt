package com.enterprise.pos.payment.adapter

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.payment.model.CreatePaymentRequest
import com.enterprise.pos.payment.model.PaymentEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CashPaymentProviderTest {

    @Test
    fun `cash provider succeeds and emits success event`() = runTest {
        val provider = CashPaymentProvider()
        provider.initialize(com.enterprise.pos.payment.model.ProviderConfig())
        val handle = provider.createPaymentIntent(
            CreatePaymentRequest(orderId = OrderId("o1"), amount = Money.of(20.0))
        ).getOrThrow()
        val events = mutableListOf<PaymentEvent>()
        val result = provider.collectPayment(handle) { events.add(it) }
        assertThat(result).isInstanceOf(com.enterprise.pos.core.Result.Success::class.java)
        val payment = (result as com.enterprise.pos.core.Result.Success).value
        assertThat(payment.amount.minorUnits).isEqualTo(2000L)
        assertThat(events).contains(PaymentEvent.Success("Cash accepted"))
    }
}
