package com.enterprise.pos.backend.plugins

import com.enterprise.pos.backend.config.BackendConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

/**
 * Security configuration:
 *  - HTTPS-only in production (handled by reverse proxy / load balancer)
 *  - API key auth for POS clients (`Authorization: Bearer <POS_API_KEY>`)
 *  - JWT auth for merchant portal (future)
 *  - Default security headers
 */
fun Application.configureSecurity() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
        header("Referrer-Policy", "no-referrer")
        header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload")
    }

    install(Authentication) {
        bearer(name = "pos-api-key") {
            realm = "Enterprise POS"
            authenticate { tokenCredential ->
                val config = BackendConfig.fromEnv()
                if (tokenCredential.token == config.posApiKey) {
                    UserIdPrincipal("pos")
                } else null
            }
        }
        jwt(name = "merchant-jwt") {
            // JWT verification skipped in skeleton — wire with your JWT secret in production.
            // verifier(JWK.parse(BackendConfig.fromEnv().jwtSecret))
            // realm = "Enterprise POS Merchant"
            // validate { credential -> if (credential.payload.subject.isNotEmpty()) JWTPrincipal(credential.payload) else null }
        }
    }
}
