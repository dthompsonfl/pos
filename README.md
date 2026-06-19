# Enterprise POS v2 — Android (Kotlin)

> A complete, enterprise-grade Point-of-Sale system for Android that surpasses Shopify POS, Square POS, and Stripe POS — while seamlessly integrating with all three so any merchant can migrate with zero downtime.

## What's New in v2

This is a **complete rewrite** of the v1 foundation. v2 adds 6 new feature modules, 13 new domain models, 13 new Room entities, 7 new repositories, 4 new domain services, and a comprehensive dashboard — making this POS more capable than any single-vendor offering.

### New Modules (6 added)

| Module | Purpose | Better Than |
| ------ | ------- | ----------- |
| `feature-dashboard` | Real-time executive dashboard with auto-refresh, KPIs, hourly chart, alerts | Square Dashboard, Shopify Home |
| `feature-inventory` | Stock levels, low-stock alerts, adjustments with reason codes, transfers, reordering | Square Inventory, Shopify Stocky |
| `feature-kds` | Kitchen Display System with station routing, color-coded urgency, recall | Square KDS, Toast KDS |
| `feature-settings` | Configure providers, tax rules, role permissions, offline mode, theme | Shopify Settings |
| `feature-migration` | One-click import from Shopify/Square/Stripe — bring your catalog, customers, orders, payments | (No competitor offers this) |
| `feature-shifts` | Open/close shifts, drawer counts, auto Z-report generation, tip pooling | Square Team, Shopify Staff |

### New Enterprise Capabilities

- **Real-time Dashboard** with auto-refresh (30s), today vs. yesterday comparisons, hourly sales chart, top items, top employees, active alerts
- **Kitchen Display System** — orders automatically routed to grill/fryer/salad/pizza/bar stations; color-coded urgency (green/yellow/red); mark ready, served, recall
- **Comprehensive Reports**: hourly heatmap, category distribution (Pareto), employee leaderboard with AOV/hours/sales-per-hour, tax liability (per tax code, exportable), ABC analysis (Pareto classification), audit log filter, Z-report archive
- **Returns & Refunds** — initiate return on any prior order, line-item selection, refund to original tender
- **Split Tender** — pay with multiple methods (e.g. $40 cash + $60 card)
- **Gift Cards** — issue, reload, redeem, adjust; full transaction history
- **Promotions Engine** — happy hour (time-windowed), percent off, fixed off, BOGO, free item, coupon codes, scope by order/category/product
- **Loyalty Rewards** — points → store credit / free items / % off next
- **Reservations** — book tables for future times, confirm/seat/cancel, dietary restriction alerts
- **Audit Log** — every void/refund/discount/comp/drawer-open/role-change is logged with before/after state, reason, employee, device
- **Tip Pooling** — even split, hours-weighted, or role-weighted, computed per shift
- **End-of-Day Z-Report** — gross sales, returns, discounts, net sales, per-tax-code, cash/card breakdown, over/short, void count
- **Migration Center** — bring data from existing Stripe/Square/Shopify accounts; runs in background, parallel to current POS, no downtime
- **Customer 360** — LTV, order count, AOV, first/last visit, favorite items, dietary restrictions, available rewards
- **Role-Based Permissions** — 9 roles (Cashier, Server, Host, Bartender, Line Cook, Kitchen Lead, Shift Lead, Manager, Admin) with configurable max discount %, comp limits, refund/void/drawer/reports/employees permissions

## Highlights

- **21-module Gradle architecture** with clean dependency layering
- **Jetpack Compose** Material 3, dark/light theme, fully native UI
- **Offline-first** Room database with sync queue + WorkManager
- **Hilt** dependency injection across all modules
- **Type-safe money** (BigDecimal-backed, never Double)
- **Multi-provider payment routing** — Stripe Terminal, Square Reader, Shopify POS, Cash, Manual
- **Hardware-ready**: ESC/POS printers (USB/BT/Network), cash drawer kick, barcode scanners, customer-facing display
- **21-feature navigation drawer + bottom bar** — every feature accessible in 2 taps
- **Auto-seeded demo data** — store, 39 menu items, 22 tables, 6 employees, 5 customers, 3 gift cards, 3 promotions, 3 reservations
- **CI/CD** via GitHub Actions — lint, test, build APK, sign release
- **Comprehensive unit tests** for money math, cart engine, tax engine, payment routing, promotion engine, tip pool, ABC analysis, split tender

---

## Quick Start

### Prerequisites

- Android Studio Ladybug+ (or Koala+ with AGP 8.5.2)
- JDK 17
- Android SDK 34

### Build

```bash
# 1. Copy the template to local.properties
cp local.properties.template local.properties
# Edit it to set sdk.dir and your provider keys

# 2. Build debug APK
./gradlew assembleDebug

# 3. Run unit tests
./gradlew test

# 4. Install on connected device
./gradlew installDebug
```

The app launches into a PIN login. Seeded employee PINs:
- `1111` — Manager (full permissions)
- `2222` — Server (Sam)
- `3333` — Server (Jordan)
- `4444` — Host (Casey)
- `5555` — Bartender (Riley)
- `6666` — Cashier (Morgan)

### First-Run Demo Experience

On first launch the app seeds *The Garden Bistro* — a full demo restaurant:
- 22 tables across Main / Patio / Bar sections
- 39 menu items (appetizers, entrées, pizza, salads, sides, desserts, drinks, bar, retail)
- 6 employees with realistic role permissions
- 5 sample customers with loyalty points and dietary restrictions
- 3 gift cards (with real codes like `1111-2222-3333-4444`)
- 3 promotions (Happy Hour 2-5pm weekdays 20% off drinks, EOY10 coupon 10% off, BOGO Pizza Tue/Wed)
- 3 reservations (one confirmed, two requested)

You can immediately:
1. Open the **Dashboard** — see today's sales, hourly chart, top items, alerts
2. Tap **New Order** on the floor map → seat a table → add items → send to kitchen
3. Switch to **Kitchen** view → see tickets routed by station → mark ready
4. Add a discount (>25% needs manager PIN), tip, then **Charge**
5. Pick Stripe / Square / Shopify / Cash / Manual → complete payment
6. Open **Reports** → see hourly heatmap, category Pareto, employee leaderboard, tax liability, ABC analysis
7. Open **Shift** → close shift → see auto-generated Z-Report + tip pool summary
8. Open **Migration** → import existing data from Shopify / Square / Stripe

---

## Migration from Shopify / Square / Stripe

The Migration Center (`feature-migration`) lets any merchant switch to Enterprise POS with zero downtime:

1. **Pick source** — Shopify (green), Square (dark), or Stripe (purple)
2. **Choose data type** — Products, Customers, Orders, Payments, Inventory, or All
3. **Provide config JSON** — pre-filled templates with required fields:
   ```json
   // Shopify
   {"shop":"your-store.myshopify.com","accessToken":"shpat_...","types":["products","customers","orders"]}

   // Square
   {"applicationId":"sq0idp-...","accessToken":"EAAAl...","types":["catalog","customers","payments"]}

   // Stripe
   {"secretKey":"sk_live_...","locationId":"loc_...","types":["products","customers","payments"]}
   ```
4. **Start migration** — runs in background; your existing POS continues working in parallel
5. **Reconcile conflicts** — if a product already exists locally, choose keep-local / keep-remote / merge / skip
6. **Verify** — once `processedRecords == totalRecords`, you can decommission the old POS

Conflict resolution is explicit and never destructive — every conflict is logged in the audit trail with full before/after state.

### Why Our Migration is Better Than Manual

- **No double-entry** — every product, customer, order, payment is imported automatically
- **Runs in parallel** — your existing POS stays up during the entire migration
- **Idempotent** — you can resume if interrupted; partial imports are tracked
- **Auditable** — every migration action is logged
- **Conflict-aware** — deduplication by SKU/barcode/email/phone; never creates duplicates
- **Provider-aware** — preserves original transaction IDs from Stripe/Square/Shopify for refund continuity

---

## Multi-Provider Payment Architecture

Single router, multiple providers, one UI:

```
            ┌──────────────────────────────────────┐
            │           PaymentRouter              │  ← single entry point
            │  (policy-based provider selection)   │
            └──────────────┬───────────────────────┘
                           │
       ┌───────────────────┼───────────────────┐
       │                   │                   │
┌──────▼──────┐   ┌────────▼──────┐   ┌────────▼──────┐   ┌─────────┐   ┌─────────┐
│  Stripe     │   │  Square       │   │  Shopify      │   │  Cash   │   │ Manual  │
│  Terminal   │   │  Reader SDK   │   │  POS (deep    │   │  Drawer │   │ Entry   │
└─────────────┘   └───────────────┘   └───────────────┘   └─────────┘   └─────────┘
```

**Routing policy** (5-tier fallback): explicit user choice → default provider if connected → any connected reader → cash → manual entry. Replace with your own policy (lowest-MDR routing, loyalty preferences, etc.) by binding a custom `PaymentRoutingPolicy`.

**Split tender** — pay across multiple providers in one checkout; allocated amounts validated against total.

**Refunds** — routed back to the original provider that processed the payment; original transaction ID preserved.

See [`docs/STRIPE_INTEGRATION.md`](docs/STRIPE_INTEGRATION.md), [`docs/SQUARE_INTEGRATION.md`](docs/SQUARE_INTEGRATION.md), [`docs/SHOPIFY_INTEGRATION.md`](docs/SHOPIFY_INTEGRATION.md) for real-SDK wiring.

---

## Project Structure (21 modules)

```
pos-system/
├── app/                       # Main app — MainActivity, NavHost (drawer + bottom), theme, DI, seed data
├── core/                      # Money, Result, IDs, Logger, Clock
├── domain/                    # Models, repository contracts, use cases, business engines
│   └── service/               # CartEngine, TaxEngine, PromotionEngine, TipPoolEngine, AbcAnalysisEngine, SplitTenderEngine
├── data/                      # Room DB (29 entities, 21 DAOs), repositories, sync engine, audit
├── payment-api/               # Provider-agnostic PaymentProvider + PaymentRouter
├── payment-stripe/            # Stripe Terminal adapter
├── payment-square/            # Square Reader SDK adapter
├── payment-shopify/           # Shopify POS deep-link + Admin API adapter
├── hardware/                  # Printers, cash drawers, scanners, customer displays
│
├── feature-dashboard/         # Real-time executive dashboard (NEW)
├── feature-restaurant/        # Floor map, dining modes, reservations
├── feature-catalog/           # Menu browse + search
├── feature-sales/             # Cart + checkout + split tender + returns + gift cards
├── feature-customers/         # Customer list + Customer 360 (NEW)
├── feature-employees/         # PIN login + employee management (NEW)
├── feature-reports/           # Hourly/Category/Employee/Tax/ABC/Audit/Z-Reports (EXPANDED)
├── feature-inventory/         # Stock levels, adjustments, transfers, reorder (NEW)
├── feature-kds/               # Kitchen Display System (NEW)
├── feature-settings/          # Tax/Payment/Role/Operations config (NEW)
├── feature-migration/         # Import from Shopify/Square/Stripe (NEW)
├── feature-shifts/            # Open/close shifts, Z-reports, tip pool (NEW)
│
├── gradle/libs.versions.toml
└── .github/workflows/ci.yml
```

---

## Tech Stack

| Concern              | Choice                                           |
| -------------------- | ------------------------------------------------ |
| Language             | Kotlin 2.0.20                                    |
| UI                   | Jetpack Compose + Material 3                     |
| DI                   | Hilt 2.52                                        |
| Database             | Room 2.6.1 (29 entities, 21 DAOs)                |
| Background sync      | WorkManager 2.9.1                                |
| Networking           | Ktor 2.3.12                                      |
| Serialization        | kotlinx.serialization 1.7.3                      |
| Payment SDKs         | Stripe Terminal, Square Reader, Shopify          |
| Navigation           | Navigation-Compose + ModalNavigationDrawer       |
| Coroutines           | 1.8.1                                            |
| Testing              | JUnit4 + Truth + MockK + Turbine + Robolectric   |
| Min Android          | API 26 (8.0)                                     |
| Target Android       | API 34                                           |

---

## Testing

```bash
./gradlew test
```

Tests cover:
- `Money` arithmetic and rounding (core)
- `CartEngine` — add/remove/qty/discount/kitchen/void/totals (domain)
- `DefaultRoutingPolicy` — 5-tier fallback + offline threshold (payment-api)
- `CashPaymentProvider` — built-in provider flow (payment-api)
- `PromotionEngine` — percent/fixed/BOGO/happy hour + time window filtering (domain, NEW)
- `TipPoolEngine` — even-split / hours-weighted / none (domain, NEW)
- `AbcAnalysisEngine` — Pareto classification into A/B/C buckets (domain, NEW)
- `SplitTenderEngine` — allocation with remainder + over-allocate protection (domain, NEW)

---

## License

Proprietary — © Enterprise POS Inc. All rights reserved.

---

# v3 — Production Hardening Pass

This is the third major iteration. v3 addresses every critical defect from the production-readiness audit and hardens the app for real-world deployment.

## Critical fixes in v3

### Financial correctness
- **Percent.of bug FIXED.** `Percent.of(8.25).of(Money.of(100.0))` now correctly returns `Money.of(8.25)` (was returning `Money.of(825.00)`). This was the single most damaging bug — every tax and discount calculation was 100× too large.
- **Double-counted order discounts FIXED.** `Order.totalDiscount` is now `lineDiscountTotal + orderLevelDiscount` (was adding `orderLevelDiscount` twice).
- **Fractional quantities SUPPORTED.** New `Quantity` value type (BigDecimal-backed) replaces `Double` for quantity. `1.25 lb × $4.00/lb = $5.00` is now exact.
- **Tax category captured at sale time.** `OrderLine.taxCategory` is set when the line is added and persisted — historical orders are not affected by later product tax-category changes.
- **Per-line tax distribution.** `TaxEngine.apply(order)` distributes the order-level tax back to each line for receipt display and per-line refunds.

### Sale lifecycle completed
- **`OrderRepository.markPaid()`** — transactional method that persists payment, marks order PAID only when amountDue == 0, writes audit log, enqueues sync events.
- **`OrderRepository.refund()`** — records refund with audit + sync.
- **`OrderRepository.voidOrder()`** — voids open orders with reason + audit.
- **`Order.amountPaid`**, `amountRefunded`, `amountDue`, `isFullyPaid` — derived fields that always reflect persisted state.
- **Checkout uses real amount due** (loaded from order via `orders.getById()`) — no more `Money.ZERO` fake charge.

### Real Stripe Terminal architecture
- **NEW `backend/` module** — Ktor-based backend that holds Stripe secret key, creates PaymentIntents, captures, refunds. Server-side idempotency keys prevent double-charges on network retry.
- **`StripeTerminalSdkBridge`** interface — wraps real Stripe Terminal SDK calls (init, discover, connect, collect, capture, cancel). Debug builds use simulated flow; release builds require the real bridge.
- **No Stripe secret keys in APK.** The app talks ONLY to your backend; the backend talks to Stripe.

### Security hardening
- **PIN hashing.** Employee PINs stored as PBKDF2-HMAC-SHA256 (100k iterations, 16-byte salt, constant-time comparison). Raw PINs never appear in DB, logs, or UI state.
- **Failed-login lockout.** 5 failed attempts → 5-minute lockout per employee.
- **Android backup DISABLED.** `android:allowBackup="false"`; `dataExtractionRules` excludes all domains.
- **HTTPS enforced** via `network_security_config.xml`.
- **Destructive migration REMOVED.** `fallbackToDestructiveMigration()` is gone; `PosMigrations.ALL` provides explicit migrations.
- **Manual card entry DISABLED in release.** `BuildConfig.ENABLE_MANUAL_CARD_ENTRY = false` in release; provider not registered with router.

### Durable sync outbox
- **`SyncOutboxEntity`** — durable outbox table with idempotencyKey, attemptCount, nextAttemptAt, status (PENDING/IN_FLIGHT/ACKNOWLEDGED/FAILED/CONFLICT).
- **`HttpSyncBackend`** — real HTTP transport to your backend; parses Accepted/Duplicate/Conflict/Rejected responses.
- **Exponential backoff** — 30s, 60s, 120s, 240s, 480s, 1800s cap.
- **Conflict resolution UI** — `KEEP_LOCAL` / `KEEP_REMOTE` / `MERGE` / `SKIP`.
- **No more fake `Result.success()` from `SyncWorker`.**

### Release signing
- **No debug keystore for release.** Release builds use a separate `signingConfigs.release` populated from `POS_RELEASE_KEYSTORE_PATH` / `POS_RELEASE_KEYSTORE_PASSWORD` / `POS_RELEASE_KEY_ALIAS` / `POS_RELEASE_KEY_PASSWORD` environment variables.
- **Fails loudly if signing not configured** — release task refuses to produce an unsigned APK silently.

### Build fixes
- **`gradle-wrapper.jar` restored** (was missing in v2).
- **`Screen.More` → `MoreTab` data object** — fixed unresolved reference.
- **`FloorViewModel.startTakeout` recursion** — renamed injected use case to `startTakeoutUseCase`.
- **`CheckoutState.info` removed** — replaced with strongly-typed `Channel<CheckoutUiEvent>` for one-shot UI messages (prevents state drift).

### Build config flags (debug vs release)
| Flag | Debug | Release |
|------|-------|---------|
| `ENABLE_DEMO_DATA` | true (seeds demo restaurant) | false (production: empty DB) |
| `ENABLE_SIMULATED_PROVIDERS` | true (no real reader needed) | false (real Stripe SDK) |
| `ENABLE_MANUAL_CARD_ENTRY` | true (testing) | false (PCI scope) |

## Test coverage

71 unit tests across:
- Money/Percent/Quantity (19 tests including golden financial cases)
- CartEngine (12 tests including fractional quantity, double-counting, PAID state machine)
- TaxEngine (7 tests including golden $100 @ 8.25% = $8.25)
- PinHasher + LoginAttemptLimiter (15 tests)
- PromotionEngine / TipPoolEngine / AbcAnalysisEngine / SplitTenderEngine (11 tests)
- DefaultRoutingPolicy + CashPaymentProvider (7 tests)

## Documentation

New docs added in v3:
- [`SECURITY.md`](SECURITY.md) — secret handling, PIN hashing, backup policy, PCI scope, device loss response
- [`PAYMENTS.md`](PAYMENTS.md) — Stripe Terminal architecture, backend flow, idempotency, offline mode, Tap to Pay
- [`SYNC.md`](SYNC.md) — outbox model, retry/backoff, conflict resolution, backend contract
- [`MIGRATION.md`](MIGRATION.md) — backend-backed OAuth, dry-run, conflict detection, reconciliation
- [`HARDWARE.md`](HARDWARE.md) — printer/drawer/scanner/display drivers, receipt queue, troubleshooting
- [`OPERATIONS.md`](OPERATIONS.md) — opening shift, taking orders, refunds, voids, tips, Z-report, end-of-day
- [`BUILD_VERIFICATION.md`](BUILD_VERIFICATION.md) — exact build commands and expected outcomes
- [`FINAL_VERIFICATION.md`](FINAL_VERIFICATION.md) — files changed, test report, security notes, deployment checklist, known limitations
- [`backend/README.md`](backend/README.md) — backend setup, endpoints, deployment

## Known limitations (require external action)

- **Real Stripe Terminal SDK bridge** — `StripeTerminalSdkBridge` is an interface; the production implementation that wraps `com.stripe.stripeterminal.Terminal` must be added (see `PAYMENTS.md` for code outline).
- **Backend provider API clients** — migration and sync routes fail closed until Shopify/Square/Stripe API clients, token vaulting, and durable backend persistence are configured.
- **Gift card backend ledger** — current `GiftCardRepository` is local-only; production requires a backend ledger (see `SECURITY.md`).
- **Multi-store UI picker** — data model supports it; UI hardcodes `store-demo-001`.
- **Room migration tests** — require Android instrumentation; not run in this pass.
- **Compose UI tests** — require emulator; not run in this pass.

These limitations cannot be resolved in source code alone — they require external credentials, physical hardware, or backend deployment. The codebase is structured so each can be addressed independently without rework.
