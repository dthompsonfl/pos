# Enterprise POS Android

Enterprise POS is a multi-module Android/Kotlin point-of-sale application prototype for restaurant and retail workflows. It includes Compose UI modules, Room persistence, domain services, payment-provider abstractions, hardware abstractions, a Ktor backend module, and CI wiring.

This repository is **not yet production-ready**. The current main branch is being hardened toward production, but several workflows still require real backend persistence, provider credentials, physical hardware, migration workers, and full validation before merchant deployment.

## Current Status

| Area | Status | Notes |
| --- | --- | --- |
| Android build setup | In progress | CI now separates Android and backend jobs. Local verification still requires Android SDK and Gradle distribution access. |
| Release signing | Hardened | Release builds require explicit signing environment variables and must not fall back to debug signing. |
| Room schema/migrations | Hardened | Schema export is enabled and the v2 to v3 migration is registered. Migration registration has a unit test. |
| POS shell/navigation | In progress | The app has login gating, top status bar, bottom navigation, drawer menu, and tablet navigation rail. More sub-feature routes still need deeper CRUD screens. |
| Payments | Mixed | Cash provider is local. Stripe real mode fails closed unless a real Terminal SDK bridge and backend are configured. Simulated providers are debug-only. Square/Shopify payment providers are not production payment paths. |
| Hardware | Mixed | Network ESC/POS printing uses a real TCP socket. USB/Bluetooth printer, USB relay drawer, and customer display paths fail closed until implemented. |
| Sync/backend | Scaffolded | Backend and sync contracts exist, but production needs durable persistence, auth/merchant isolation, idempotency storage, and conflict handling validation. |
| Migration | Scaffolded | Migration must be backend/OAuth/token-vault driven. Android must not collect raw Shopify, Square, or Stripe secrets. |
| Reports/Z-report | In progress | Reporting must be verified against persisted orders, tenders, refunds, inventory movements, shifts, and sync facts before production use. |
| Security | In progress | Raw provider secrets are not intended for Android. Release disables simulated/manual card paths. Additional session/device/manager-override hardening is still required. |

## What Is Intentionally Disabled Or Fail-Closed In Release

- Fake card success paths.
- Manual card entry outside debug builds.
- Square and Shopify as production payment providers unless real integrations are added.
- Stripe card-present collection when a real Terminal SDK bridge is not bound.
- USB/Bluetooth printer success without real transport code.
- USB relay cash drawer success without real transport code.
- Customer display success without a configured secondary display implementation.
- Release signing without explicit keystore credentials.
- Destructive Room migrations.

## Repository Structure

```text
app/                       Main Android app, shell, DI, theme, seed wiring
core/                      Money, quantity, IDs, result/error model, utilities
domain/                    Models, repository contracts, use cases, business engines
data/                      Room database, DAOs, repositories, sync outbox pieces
payment-api/               Provider-neutral payment contracts and router
payment-stripe/            Stripe backend/Terminal adapter, debug simulation guarded by build type
payment-square/            Square adapter scaffold/debug provider
payment-shopify/           Shopify adapter scaffold/debug provider
hardware/                  Printer, drawer, scanner, customer display abstractions
feature-dashboard/         Dashboard UI
feature-restaurant/        Floor/table/reservation UI
feature-catalog/           Catalog/menu UI
feature-sales/             Cart/checkout UI
feature-customers/         Customer list/detail UI
feature-employees/         Login and employee management UI
feature-reports/           Reports UI
feature-inventory/         Inventory UI
feature-kds/               Kitchen display UI
feature-settings/          Settings UI
feature-migration/         Migration UI
feature-shifts/            Shift/Z-report UI
backend/                   Ktor backend module scaffold
```

## Build And Test

Prerequisites:

- JDK 17.
- Android SDK 34.
- Gradle wrapper access to the configured Gradle distribution.
- `local.properties` with `sdk.dir` for local Android builds.

Recommended verification from a clean checkout:

```bash
./gradlew --version
./gradlew clean
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
./gradlew :backend:test
./gradlew :backend:installDist
```

Release build requires secure signing inputs:

```bash
POS_RELEASE_KEYSTORE_PATH=release.keystore \
POS_RELEASE_KEYSTORE_PASSWORD=... \
POS_RELEASE_KEY_ALIAS=... \
POS_RELEASE_KEY_PASSWORD=... \
./gradlew assembleRelease
```

## Production Requirements Still Needed

Before using this app with real merchants, complete and verify at least:

1. Real Stripe Terminal SDK bridge binding and reader lifecycle testing.
2. Backend auth, merchant/store/register isolation, durable idempotency, webhooks, and reconciliation.
3. Durable sync outbox processing with conflict visibility and retry validation.
4. Complete sale lifecycle validation: cart, checkout, tenders, refunds, voids, receipts, inventory decrement, audit, sync, reports, and Z-report reconciliation.
5. CRUD completion and permission enforcement for all major business entities.
6. First-run onboarding for store, register, employee, tax, payment, hardware, receipt, and shift setup.
7. Physical hardware validation for printers, drawers, scanners, readers, and customer displays.
8. Migration OAuth/token-vault/jobs/workers/conflict-resolution flow.
9. Security hardening for employee sessions, failed login lockout, manager override, device registration, and audit ingestion.

## Security Notes

- Do not store Stripe secret keys, Square access tokens, or Shopify access tokens in Android UI, resources, Gradle properties, or `local.properties`.
- Do not store raw employee PINs.
- Do not use floating-point money math in new code.
- Do not enable destructive Room migrations for production builds.
- Do not ship release builds signed with a debug keystore.
- Treat debug-seeded data and simulated providers as development-only.
