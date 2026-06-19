package com.enterprise.pos.backend.plugins

import com.enterprise.pos.backend.routes.*
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        terminalConnectionTokenRoute()
        paymentIntentsRoute()
        captureRoute()
        refundsRoute()
        paymentLookupRoute()
        syncEventsRoute()
        migrationRoutes()
    }
}
