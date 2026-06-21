package com.enterprise.pos.backend.plugins

import com.enterprise.pos.backend.config.BackendConfig
import com.enterprise.pos.backend.routes.*
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val config = BackendConfig.fromEnv()
    routing {
        terminalConnectionTokenRoute()
        paymentIntentsRoute()
        captureRoute()
        refundsRoute()
        paymentLookupRoute()
        syncEventsRoute()
        migrationRoutes()
        stripeWebhookRoute(config)
    }
}
