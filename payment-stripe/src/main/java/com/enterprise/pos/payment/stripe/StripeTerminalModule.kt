package com.enterprise.pos.payment.stripe

import android.content.Context
import com.enterprise.pos.core.Logger
import com.enterprise.pos.payment.model.PaymentProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for the Stripe Terminal payment provider.
 *
 * Provides [StripeTerminalPaymentProvider] as a singleton and binds it to the
 * [PaymentProvider] interface so the payment router can inject it.
 *
 * Configuration values (backend URL, token endpoint) are injected from [AppModule]
 * via [Named] qualifiers so the `payment-stripe` library module does not need
 * direct access to the app-level [BuildConfig].
 */
@Module
@InstallIn(SingletonComponent::class)
object StripeTerminalModule {

    private const val DEFAULT_CONNECTION_TOKEN_ENDPOINT = "/v1/terminal/connection-token"

    @Provides
    @Singleton
    fun provideStripeTerminalPaymentProvider(
        @ApplicationContext context: Context,
        @Named("backend_base_url") backendBaseUrl: String,
        @Named("enable_simulated_providers") simulate: Boolean,
        authTokenProvider: com.enterprise.pos.core.security.AuthTokenProvider,
        logger: Logger
    ): StripeTerminalPaymentProvider {
        return StripeTerminalPaymentProvider(
            context = context,
            backendBaseUrl = backendBaseUrl,
            connectionTokenEndpoint = DEFAULT_CONNECTION_TOKEN_ENDPOINT,
            authTokenProvider = authTokenProvider,
            logger = logger,
            simulate = simulate
        )
    }

    @Provides
    @Singleton
    fun providePaymentProvider(
        provider: StripeTerminalPaymentProvider
    ): PaymentProvider = provider
}
