# Enterprise POS Backend

A reference Spring Boot (or Ktor) backend implementing the secure server-side endpoints required by the Enterprise POS Android app.

## Why a backend is mandatory

The Android app **must not** store Stripe secret keys, Shopify access tokens, or Square access tokens. Per PCI SSC guidance:

- Card-present payments require server-side PaymentIntent creation and capture.
- Provider OAuth tokens for migration must be vaulted server-side.
- Sync outbox events must be received and deduplicated by the server.

The Android app talks ONLY to this backend. All provider secrets live here, in environment variables — never in source control, never in the APK.

## Endpoints

### Stripe Terminal
| Method | Path | Purpose |
| ------ | ---- | ------- |
| POST   | `/v1/terminal/connection-token` | Issue a short-lived Stripe Terminal connection token |
| POST   | `/v1/payments/payment-intents` | Create a PaymentIntent for a card-present payment |
| POST   | `/v1/payments/{id}/capture` | Capture an authorized PaymentIntent (with optional tip) |
| POST   | `/v1/refunds` | Refund a previously captured charge |
| GET    | `/v1/payments/{id}` | Look up a payment's status |

### Sync Outbox
| Method | Path | Purpose |
| ------ | ---- | ------- |
| POST   | `/v1/sync/events` | Receive an outbox event (idempotent by `Idempotency-Key` header) |
| POST   | `/v1/sync/events/{id}/resolve` | Resolve a conflict (KEEP_LOCAL / KEEP_REMOTE / MERGE / SKIP) |

### Migration
| Method | Path | Purpose |
| ------ | ---- | ------- |
| GET    | `/v1/migration/shopify/oauth/start` | Begin Shopify OAuth flow |
| GET    | `/v1/migration/shopify/oauth/callback` | Shopify OAuth callback |
| GET    | `/v1/migration/square/oauth/start` | Begin Square OAuth flow |
| GET    | `/v1/migration/square/oauth/callback` | Square OAuth callback |
| POST   | `/v1/migration/jobs` | Start a migration job (dry-run or real) |
| GET    | `/v1/migration/jobs/{id}` | Poll migration job status |
| POST   | `/v1/migration/jobs/{id}/conflicts/{conflictId}/resolve` | Resolve a migration conflict |

## Configuration

All secrets via environment variables — never committed to source:

```bash
# Stripe
STRIPE_SECRET_KEY=sk_live_or_sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Shopify
SHOPIFY_CLIENT_ID=...
SHOPIFY_CLIENT_SECRET=...
SHOPIFY_SCOPES=read_products,read_customers,read_orders

# Square
SQUARE_APPLICATION_ID=sq0idp-...
SQUARE_APPLICATION_SECRET=sq0csp-...
SQUARE_ENVIRONMENT=sandbox|production

# Database (server-side, holds merchant tokens, sync state, audit log)
DATABASE_URL=postgres://user:pass@host:5432/pos_backend
REDIS_URL=redis://...

# Auth
JWT_SECRET=...
POS_API_KEY=...

# App
PORT=8080
ENVIRONMENT=production
```

## Reference implementation

See `src/main/kotlin/com/enterprise/pos/backend/` for a Ktor-based skeleton that implements the endpoint contracts the Android app expects. The actual Stripe SDK calls, Shopify Admin API client, and Square Connect client are wired but require credentials to run.

## Running locally

```bash
cp .env.example .env
# edit .env with your test credentials
./gradlew :backend:run
```

## Production deployment

- Deploy behind HTTPS only.
- Use managed secrets (AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault).
- Enable audit log streaming to your SIEM.
- Set up database backups.
- Configure Stripe webhook endpoint to receive `payment_intent.succeeded`, `charge.refunded` events.
