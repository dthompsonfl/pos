# Security

## Current Status

Security hardening is in progress. The repository contains important safeguards, but it is **not yet ready for production security sign-off**. Remaining work includes complete employee/session/device enforcement, manager override flows, backend-backed audit ingestion, durable token vaulting, merchant isolation, and end-to-end permission enforcement across all privileged use cases.

## Secret Handling

**Never store on the Android device:**

- Stripe secret keys (`sk_live_*` / `sk_test_*`).
- Shopify access tokens (`shpat_*`).
- Square access tokens (`EAAA*`).
- Backend admin credentials.
- Provider OAuth refresh tokens.

**Server-side only:**

- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `SHOPIFY_CLIENT_SECRET`
- `SQUARE_APPLICATION_SECRET`
- `JWT_SECRET`
- `POS_API_KEY`
- `DATABASE_URL`

**OK to embed in APK because they are not secrets:**

- `STRIPE_LOCATION_ID`
- `SQUARE_APPLICATION_ID`
- `SHOPIFY_SHOP_DOMAIN`
- `BACKEND_BASE_URL`

Android should talk to the POS backend. The backend should talk to Stripe, Shopify, and Square with server-held credentials.

## PIN Hashing

Employee PIN storage is designed around PBKDF2-HMAC-SHA256 hashes with random salt and constant-time comparison. Raw PINs must not be stored in Room, logs, sync payloads, or backend records.

Remaining auth work before production:

- Employee selector/badge/code plus PIN, not PIN-only global lookup.
- Persisted failed-attempt counters and lockout/backoff verification.
- Employee, register, and device session enforcement.
- Manager override authentication with reason and audit trail.

## Local Storage

Room may store orders, tenders, customers, employee records, inventory facts, audit entries, sync events, and operational settings. It must not store raw card data, CVV, magnetic stripe track data, provider secret keys, or provider OAuth tokens.

`android:allowBackup="false"` is set, and backup/data extraction rules should continue excluding POS data domains.

## Android Permissions

Current manifest posture:

- Internet/network state are required for backend sync and payments.
- Bluetooth and camera permissions support readers, printers, scanners, and barcode workflows.
- Microphone permission is not requested.

Any new sensitive permission must be tied to a real implemented feature and documented here.

## Payment Scope

The app must stay out of raw card-data handling:

- Card-present payments should go through Stripe Terminal or another certified provider UI/SDK.
- Manual card entry is disabled in release builds.
- Simulated providers are debug-only.
- Release Stripe mode fails closed when no real Terminal bridge is configured.

## Database Migrations

- Production database construction must not use `fallbackToDestructiveMigration()`.
- Room schema export is enabled.
- Known migrations are registered in `PosMigrations.ALL`.
- Migration tests should be added with every schema change.

## Audit Log

Audit logging is a required production control, but coverage must be verified per use case before release.

Production audit events must include:

- Actor employee/session.
- Approver for manager override actions.
- Action, entity type, and entity id.
- Before/after snapshot or hash where feasible.
- Reason for sensitive actions.
- Store, register, and device identifiers.
- Timestamp and sync status.

Remaining audit work:

- Verify every privileged state-changing use case writes an audit event.
- Sync audit events to a backend store that users cannot tamper with locally.
- Add server-side audit ingestion and retention policy.
- Add support bundle export with secrets redacted.

## Gift Cards And Store Credit

Local-only gift card or store-credit ledgers are not safe for production. Production issue, reload, redeem, refund, void, and adjustment events must be backed by a server-side ledger and reconciled with paid orders/refunds.

Until that ledger exists, gift card and store-credit financial flows must remain disabled for production redemption.

## Production Security Checklist

- [ ] Backend auth and merchant isolation implemented.
- [ ] Device registration and register assignment enforced.
- [ ] Employee sessions and lockout enforced.
- [ ] Manager override implemented and audited.
- [ ] Permission checks enforced in domain/use-case/repository layers, not only UI.
- [ ] Audit coverage verified for privileged mutations.
- [ ] Provider secrets stored only server-side.
- [ ] Release build signed with secure release keystore.
- [ ] Debug/simulated providers disabled in release.
- [ ] Hardware unsupported paths fail closed.
- [ ] Support bundle redacts secrets and PII where required.
