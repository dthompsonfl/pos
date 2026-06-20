package com.enterprise.pos.backend.config

/**
 * Backend configuration loaded from environment variables.
 * No secrets are ever read from source files — only env vars.
 */
data class BackendConfig(
    val port: Int,
    val environment: String,
    val stripeSecretKey: String,
    val stripeWebhookSecret: String,
    val shopifyClientId: String,
    val shopifyClientSecret: String,
    val shopifyScopes: String,
    val shopifyRedirectUri: String,
    val squareApplicationId: String,
    val squareApplicationSecret: String,
    val squareEnvironment: String,
    val squareRedirectUri: String,
    val databaseUrl: String,
    val redisUrl: String,
    val jwtSecret: String,
    val posApiKey: String
) {
    val isProduction: Boolean get() = environment == "production"
    val squareTokenUrl: String
        get() = if (isProduction) {
            "https://connect.squareup.com/oauth2/token"
        } else {
            "https://connect.squareupsandbox.com/oauth2/token"
        }
    val squareAuthorizeUrl: String
        get() = if (isProduction) {
            "https://connect.squareup.com/oauth2/authorize"
        } else {
            "https://connect.squareupsandbox.com/oauth2/authorize"
        }

    companion object {
        fun fromEnv(): BackendConfig = BackendConfig(
            port = (System.getenv("PORT") ?: "8080").toIntOrNull() ?: 8080,
            environment = System.getenv("ENVIRONMENT") ?: "sandbox",
            stripeSecretKey = System.getenv("STRIPE_SECRET_KEY")
                ?: throw IllegalStateException("STRIPE_SECRET_KEY environment variable is required"),
            stripeWebhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET") ?: "",
            shopifyClientId = System.getenv("SHOPIFY_CLIENT_ID") ?: "",
            shopifyClientSecret = System.getenv("SHOPIFY_CLIENT_SECRET") ?: "",
            shopifyScopes = System.getenv("SHOPIFY_SCOPES") ?: "read_products,read_customers,read_orders",
            shopifyRedirectUri = System.getenv("SHOPIFY_REDIRECT_URI") ?: "",
            squareApplicationId = System.getenv("SQUARE_APPLICATION_ID") ?: "",
            squareApplicationSecret = System.getenv("SQUARE_APPLICATION_SECRET") ?: "",
            squareEnvironment = System.getenv("SQUARE_ENVIRONMENT") ?: "sandbox",
            squareRedirectUri = System.getenv("SQUARE_REDIRECT_URI") ?: "",
            databaseUrl = System.getenv("DATABASE_URL") ?: "postgres://pos:pos@localhost:5432/pos_backend",
            redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379",
            jwtSecret = System.getenv("JWT_SECRET")
                ?: throw IllegalStateException("JWT_SECRET environment variable is required"),
            posApiKey = System.getenv("POS_API_KEY")
                ?: throw IllegalStateException("POS_API_KEY environment variable is required")
        )
    }
}
