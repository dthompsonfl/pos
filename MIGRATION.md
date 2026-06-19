# Migration Center

## Architecture

Migration FROM Shopify / Square / Stripe to Enterprise POS is **backend-backed**. The Android app NEVER collects provider secrets.

```
┌──────────────────────────────────────────────────────────────┐
│ Android POS                                                  │
│                                                              │
│ 1. User taps "Migrate from Shopify"                          │
│ 2. App opens browser to backend OAuth start URL              │
│ 3. User logs in to Shopify, grants consent                   │
│ 4. Shopify redirects to backend callback                     │
│ 5. Backend exchanges code for access token                   │
│ 6. Backend stores token in encrypted vault                   │
│ 7. Backend creates migration job, returns job ID             │
│ 8. App polls job status every 5 seconds                      │
│ 9. Backend pulls data from Shopify Admin API                 │
│ 10. Backend maps to our schema                               │
│ 11. Backend pushes to Android via sync events                │
│ 12. App shows progress + conflicts                           │
│ 13. User resolves conflicts in app                           │
│ 14. App POSTs resolution to backend                          │
│ 15. Backend applies resolution, continues                    │
│ 16. Job completes — migration done                           │
│                                                              │
│ At NO point does the app see the Shopify access token.       │
└──────────────────────────────────────────────────────────────┘
```

## Sources supported

| Source | OAuth Flow | Data Imported |
|--------|------------|---------------|
| Shopify | `shopify.admin/oauth/authorize` | Products, variants, customers, orders, payments, inventory |
| Square | `connect.squareup.com/oauth2/authorize` | Catalog, customers, payments, orders |
| Stripe | (no OAuth — uses server-side secret key) | Payments, customers, products (Stripe Prices) |
| CSV | (file upload to backend) | Products, customers |

## What the app shows

The Android Migration screen shows:
- Source picker (Shopify / Square / Stripe / CSV)
- Migration type (Products / Customers / Orders / Payments / All)
- Progress bar (processed / total records)
- Failed records count
- Conflicts list with resolution buttons

The app NEVER asks for:
- Shopify access token
- Square access token
- Stripe secret key
- Any password

## OAuth flow

When the user picks "Migrate from Shopify":

1. App calls `GET /v1/migration/shopify/oauth/start?shop=your-store.myshopify.com`
2. Backend responds with `{authUrl: "https://your-store.myshopify.com/admin/oauth/authorize?client_id=...", state: "<uuid>"}`
3. App opens `authUrl` in the system browser (Chrome Custom Tab)
4. User logs in to Shopify, grants consent for scopes (read_products, read_customers, read_orders, etc.)
5. Shopify redirects to backend's `/v1/migration/shopify/oauth/callback?code=...&state=...`
6. Backend validates `state` (CSRF protection), exchanges `code` for access token via Shopify API
7. Backend stores access token in encrypted vault (database or secrets manager)
8. Backend redirects to a deep link `enterprise-pos://migration/connected?source=shopify`
9. App receives deep link, shows "Connected" and offers to start the import

The same pattern applies to Square. Stripe doesn't use OAuth for migration — it uses the server-side secret key already in the backend's env vars.

## Dry-run mode

Before importing real data, the merchant can request a dry-run:
- Backend pulls from source API but does NOT apply to local state
- Returns counts: "Found 1,234 products, 5,678 customers, 9,012 orders"
- Merchant confirms before the real import

Dry-run is initiated via `POST /v1/migration/jobs` with `{"dryRun": true}`.

## Conflict detection

Conflicts arise when the same external entity already exists locally (e.g., from a previous partial migration). The backend detects conflicts by:
- Matching on external ID (Shopify product ID, Square catalog object ID, Stripe price ID)
- Matching on SKU / barcode for products
- Matching on email for customers
- Matching on provider transaction ID for payments

When a conflict is detected, the event is marked `CONFLICT` and surfaced to the app with:
- External entity JSON
- Local entity JSON
- Diff summary

The merchant chooses a resolution and the app POSTs it to `/v1/migration/jobs/{id}/conflicts/{conflictId}/resolve`.

## Provider ID preservation

Every imported entity preserves its original provider ID in `metadata.providerExternalId`. This is critical for:
- Refund continuity (a Stripe payment imported from Shopify can be refunded via Stripe using the original charge ID)
- Customer deduplication (a customer who exists in both Shopify and Square is merged based on email)
- Audit trail (you can always trace back to the source system)

## Reconciliation

After migration completes, the backend runs a reconciliation report:
- Total records in source system
- Total records imported
- Total records skipped (with reason)
- Total conflicts (with resolution)
- Total value migrated (sum of historical payments)

This report is downloadable as PDF/CSV from the Reports screen.

## Rollback

Migration is NOT reversible — once data is imported, deleting it would affect new orders placed on top of it. Instead:
- Each imported entity has `metadata.importedAt` and `metadata.migrationJobId`
- A "soft rollback" can hide imported entities from active views while keeping them in the database
- A full rollback requires a database restore from before the migration

## What's NOT implemented in this delivery

The current `MigrationRepositoryImpl.startJob()` simulates job completion in debug builds. For production:
1. Implement the real backend endpoints (see `backend/src/main/kotlin/com/enterprise/pos/backend/routes/MigrationRoutes.kt`)
2. Wire the backend to Shopify Admin API (`/admin/api/2024-07/products.json`, etc.)
3. Wire the backend to Square Connect API (`/v2/catalog/list`, `/v2/customers/list`, etc.)
4. Wire the backend to Stripe API (`/v1/charges`, `/v1/customers`, `/v1/prices`)
5. Add conflict detection logic in the backend
6. Add reconciliation report generation

The Android-side UI is production-ready; the backend needs the actual provider API clients.
