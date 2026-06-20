package com.enterprise.pos.payment.stripe

import android.content.Context
import com.enterprise.pos.core.Logger
import com.enterprise.pos.payment.model.PaymentProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Stripe Terminal payment provider.
 *
 * Provides [StripeTerminalPaymentProvider] as a singleton and binds it to the
 * [PaymentProvider] interface so the payment router can inject it.
 *
 * Configuration values (backend URL, token endpoint) are hardcoded here for the
 * enterprise deployment. Override by providing a custom module with higher precedence
 * or by setting build-config fields in the app module.
 */
@Module
@InstallIn(SingletonComponent::class)
object StripeTerminalModule {

    private const val DEFAULT_BACKEND_BASE_URL = "https://api.enterprise-pos.example.com"
    private const val DEFAULT_CONNECTION_TOKEN_ENDPOINT = "/v1/terminal/connection-token"

    @Provides
    @Singleton
    fun provideStripeTerminalPaymentProvider(
        @ApplicationContext context: Context,
        logger: Logger
    ): StripeTerminalPaymentProvider {
        return StripeTerminalPaymentProvider(
            context = context,
            backendBaseUrl = DEFAULT_BACKEND_BASE_URL,
            connectionTokenEndpoint = DEFAULT_CONNECTION_TOKEN_ENDPOINT,
            authTokenProvider = { null },
            logger = logger,
            simulate = false
        )
    }

    @Provides
    @Singleton
    fun providePaymentProvider(
        provider: StripeTerminalPaymentProvider
    ): PaymentProvider = provider
}
