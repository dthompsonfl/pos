package com.enterprise.pos.backend

import com.enterprise.pos.backend.config.BackendConfig
import com.enterprise.pos.backend.plugins.configureRouting
import com.enterprise.pos.backend.plugins.configureSerialization
import com.enterprise.pos.backend.plugins.configureSecurity
import com.enterprise.pos.backend.plugins.configureStripe
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Enterprise POS backend entry point.
 *
 * Wires together:
 *  - Stripe Terminal endpoints (connection-token, payment-intents, capture, refunds)
 *  - Sync outbox receiver
 *  - Migration OAuth flows (Shopify, Square)
 *  - JWT auth for POS clients
 *
 * All secrets come from environment variables (see .env.example).
 */
fun main() {
    val config = BackendConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureStripe()
    configureRouting()
}
