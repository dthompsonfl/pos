package com.enterprise.pos.backend.plugins

import com.enterprise.pos.backend.config.BackendConfig
import com.enterprise.pos.backend.stripe.StripeService
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install

/**
 * Initializes the Stripe Java SDK with the secret key from environment variables.
 * The secret key NEVER leaves the server.
 */
fun Application.configureStripe() {
    val config = BackendConfig.fromEnv()
    StripeService.initialize(config.stripeSecretKey)
}
