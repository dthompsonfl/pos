# Architecture

## Design Principles

1. **User-first UX** — every screen is designed to be operable without training. Large tap targets, predictable flows, no nested menus, prominent pricing.
2. **Type safety over the wire** — Money is a value class backed by `Long` (minor units), never Double or Float. IDs are typed wrappers around String so a CustomerId can never be passed where an OrderId is expected.
3. **Offline-first** — every write hits the local Room DB first and is queued for cloud sync. The POS works fully offline; sync drains when network returns.
4. **Provider-agnostic payments** — the entire app talks to `PaymentRouter`. Adding Stripe, Square, Shopify, or a new provider is a single module addition — no business-logic changes.
5. **Pure domain** — the `domain` module has no Android dependencies. Cart math, tax calculation, discount policy are all unit-testable in plain JVM.

## Module Layering

```
┌─────────────────────────────────────────────────────────────────┐
│                              app                                │
│  (MainActivity, NavHost, PosApplication, PosTheme, DatabaseSeeder)│
└────────────────────────────┬────────────────────────────────────┘
                             │
   ┌─────────────────────────┼──────────────────────────┐
   │                         │                          │
┌──▼─────────────┐  ┌────────▼─────────┐  ┌────────────▼─────────┐
│ feature-*      │  │ payment-*        │  │ hardware             │
│ (UI + VMs)     │  │ (provider impls) │  │ (printers, scanners) │
└──┬─────────────┘  └────────┬─────────┘  └────────────┬─────────┘
   │                         │                          │
   │           ┌─────────────┴────────────┐             │
   │           │       payment-api        │             │
   │           │  (PaymentRouter, models) │             │
   │           └─────────────┬────────────┘             │
   │                         │                          │
┌──▼─────────────────────────▼──────────────────────────▼──────┐
│                            data                              │
│  (Room, DAOs, repository implementations, sync engine, DI)   │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────┐
│                           domain                              │
│   (Models, repository contracts, use cases, CartEngine,       │
│    TaxEngine, dining flow, role permissions)                  │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────┐
│                            core                               │
│   (Money, Percent, Result, AppError, typed Ids, Clock,        │
│    DispatcherProvider, Logger, resultFlow helpers)            │
└───────────────────────────────────────────────────────────────┘
```

## Money

Money is the most important domain type. It is:

- **Immutable** — every operation returns a new `Money`.
- **Exact** — backed by `BigDecimal` internally and `Long` for storage (minor units, e.g. cents).
- **Type-safe** — the only way to get a `Money` is via `Money.of(Double)`, `Money.of(BigDecimal)`, or `Money.ofMinor(Long)` — no implicit conversions from `Double`.
- **Serializable** — kotlinx.serialization friendly via `@Serializable` on the underlying `Long`.

All arithmetic uses `BigDecimal` with explicit `RoundingMode.HALF_UP` so tax and tip calculations are deterministic across devices.

## Result & AppError

Every function that can fail returns `Result<T>` — a sealed class with `Success` and `Failure`. Errors are typed via `AppError`:

- `AppError.Payment(code, message, providerError)` — for payment failures, with stable `PaymentErrorCode` (DECLINED, CANCELLED, READER_DISCONNECTED, etc.)
- `AppError.Validation(field, message)` — form validation
- `AppError.Permission(required)` — role permission denied
- `AppError.Hardware(device, message)` — printer/drawer/scanner failures
- `AppError.Sync(conflicts)` — cloud sync conflict
- `AppError.Network`, `AppError.Auth`, `AppError.NotFound`, `AppError.Generic`

This means the UI can pattern-match on the error type to show the right localized message, retry button, or escalation prompt — never a generic "Something went wrong".

## Payment Router

The router is the single integration point between the app and the payment providers. It:

1. **Holds** a `Map<PaymentProviderId, PaymentProvider>` of all registered providers.
2. **Initializes** them all on app startup, in parallel.
3. **Discovers readers** across all providers (Bluetooth, USB, network).
4. **Maintains aggregated reader status** as a `Flow<Map<ProviderId, ReaderStatus>>` for UI consumption.
5. **Routes each payment** through the configured `PaymentRoutingPolicy`:
   - Explicit user choice
   - Default provider if connected
   - First connected provider
   - Cash fallback
   - Manual entry fallback
6. **Streams events** — every `PaymentEvent` (InsertCard, ReadingCard, Processing, Success, Declined, etc.) flows through a single callback the UI observes.
7. **Handles refunds** by delegating to the original provider — the router remembers which provider processed each payment.

### Offline Mode

If the policy decision allows it (amount ≤ `maxOfflineAmount` and provider supports `OFFLINE_MODE`), the router can queue payments locally and settle them when network returns. Stripe Terminal has first-class offline support; we model it uniformly so other providers can opt in.

## Restaurant-Specific Features

### Dining Modes

`DiningMode` is an enum on every `Order`:

- `DINE_IN_HOST_SEATED` — host seats the guest at a table, server takes order
- `DINE_IN_SELF_SEATED` — guest sits, order taken at table or counter (fast-casual)
- `TO_GO` — pickup, paid in advance or on pickup
- `DELIVERY` — fulfilled by store staff or third party
- `CATERING` — large pre-order
- `RETAIL` — non-restaurant, no table

The `FloorScreen` shows a card grid letting the cashier pick a mode in one tap. Each mode routes to the right flow: dining modes need a table selection, to-go skips it, retail skips both.

### Floor Map

The `FloorViewModel` loads all tables for the current store, grouped by section. Each table card shows:
- Status color (green = available, blue = seated, orange = ordered, etc.)
- Guest count
- Server assignment
- Capacity

Tapping an available table opens a "Seat Table" sheet — pick guest count, confirm, the order is created and the table status becomes `SEATED`. From there the cart opens directly.

### Kitchen Routing

Each `Product` has an optional `kitchenRoutingKey` (e.g. `"grill"`, `"salad"`, `"bar"`, `"pizza"`). When `sendToKitchen()` is called, the `CartEngine` marks each line `sentToKitchen = true` and the printer manager renders separate kitchen tickets per station — grill items go to the grill printer, bar items to the bar printer, etc. (In the seed data: fryer, cold, grill, pizza, saute, oven, bar.)

### Table Status Lifecycle

```
AVAILABLE → SEATED → ORDERED → DINING → BILL_REQUESTED → AVAILABLE
                ↑                 ↓
                └─── CLEANING ←───┘
RESERVED → SEATED
```

Status transitions are driven by `OrderStatus` changes — when an order moves from `OPEN` to `SENT_TO_KITCHEN`, the table moves from `SEATED` to `ORDERED`. When the order moves to `AWAITING_PAYMENT`, the table moves to `BILL_REQUESTED`. When paid, the table moves to `CLEANING` and back to `AVAILABLE` after a configurable delay.

## Tax Engine

`TaxEngine` is configurable per store. The default restaurant config:

- **Food Tax 2.5%** — on `FOOD_GROCERY` items
- **Sales Tax 8.25%** — on `STANDARD`, `PREPARED_FOOD`, `CLOTHING`

Exempt and zero-rated items pay no tax. Compound taxes (Quebec QST + GST) are supported via `TaxRule.compoundOn`.

The engine handles order-level discounts by scaling them proportionally across tax categories before computing tax.

## Sync Engine

Every write to Room enqueues a `SyncQueueEntity`. A periodic `WorkManager` job (every 15 minutes, network-constrained) drains the queue and POSTs to your backend. Failures are retried with exponential backoff (max 5 attempts).

In production you bind a `SyncBackend` implementation that:
1. Authenticates with your cloud API
2. POSTs each `SyncQueueEntity` payload
3. Returns `true` on success → row deleted from queue
4. Returns `false` on conflict → row marked failed, surfaced to a conflict-resolution UI

## Hardware

### Receipt Printers

`ReceiptRenderer` formats an `Order` + list of `Payment`s into ESC/POS bytes:

- Store header (centered)
- Order metadata (id, date, server, table, mode, guest count)
- Item lines (qty, name, total)
- Subtotal / discounts / taxes / tip / TOTAL
- Payment summary (provider, last4, brand)
- Footer + drawer kick + paper cut

Three driver implementations:
- `UsbPrinterDriver` — Android USB Host API
- `BluetoothPrinterDriver` — RFCOMM SPP
- `NetworkPrinterDriver` — TCP port 9100

Register one or more in `PrinterManager` and the router prints to whichever is connected.

### Cash Drawer

Two paths:
- `PrinterKickCashDrawerDriver` — sends ESC p m t1 t2 to a connected printer (most common)
- `UsbRelayCashDrawerDriver` — direct USB relay for dedicated drawer setups

### Barcode Scanners

- `KeyboardWedgeScanner` — handles USB HID scanners that present as a keyboard (most rugged POS scanners). The host Activity forwards KeyEvents to `onKeyEvent()` and the scanner accumulates them, emitting a complete barcode on ENTER.
- `CameraScanner` — ML Kit camera fallback for tablets without a hardware scanner

### Secondary Customer Display

- `PresentationSecondaryDisplay` — uses Android's `Presentation` API to drive a second screen over HDMI/USB-C. Shows running cart, totals, welcome and thank-you screens.

## Role-Based Permissions

`RolePermissions` is configured per `EmployeeRole`:

| Role        | Discounts | Refunds | Void | Drawer | Reports | Employees | Comp Items  |
| ----------- | --------- | ------- | ---- | ------ | ------- | --------- | ----------- |
| Cashier     | ≤5%       | ✗       | ✗    | ✓      | ✗       | ✗         | ✗           |
| Server      | ≤10%      | ✗       | ✗    | ✗      | ✗       | ✗         | ≤$25        |
| Host        | ✗         | ✗       | ✗    | ✗      | ✗       | ✗         | ✗           |
| Bartender   | ≤15%      | ✗       | ✗    | ✓      | ✗       | ✗         | ≤$50        |
| Shift Lead  | ≤25%      | ✓       | ✓    | ✓      | ✓       | ✗         | ≤$75        |
| Manager     | 100%      | ✓       | ✓    | ✓      | ✓       | ✓         | ≤$500       |
| Admin       | 100%      | ✓       | ✓    | ✓      | ✓       | ✓         | $10,000     |

`ApplyDiscountUseCase` checks `requiresManagerApproval` and rejects if the employee's role doesn't allow it. Real comp-item flow checks `maxCompValue` similarly.

## Dependency Injection

Hilt modules live in each module's `di/` package:

- `core`: `Clock`, `DispatcherProvider`, `Logger`
- `data`: `PosDatabase`, all DAOs, all repository implementations, `SyncEngine`
- `payment-api` (via `:app`'s `AppModule`): `Map<PaymentProviderId, PaymentProvider>`, `PaymentRouterConfig`, `PaymentRoutingPolicy`, `PaymentRouter`, all use cases
- `hardware`: `PrinterManager`, `CashDrawerManager`, `SecondaryDisplayController`, `BarcodeScanner`, `ReceiptRenderer`

`@HiltAndroidApp` on `PosApplication` triggers the entire graph. ViewModels use `@HiltViewModel` + `@Inject constructor`.

## Build Variants

- `debug` — debuggable, `.debug` applicationIdSuffix, `-debug` versionNameSuffix
- `release` — minified + resource-shrinking, signed with debug keystore by default (override in CI)

Payment provider credentials flow in via `BuildConfig`:

```kotlin
buildConfigField("String", "STRIPE_LOCATION_ID", "\"${project.findProperty("stripeLocationId") ?: ""}\"")
buildConfigField("String", "SQUARE_APPLICATION_ID", "\"${project.findProperty("squareApplicationId") ?: ""}\"")
buildConfigField("String", "SHOPIFY_SHOP_DOMAIN", "\"${project.findProperty("shopifyShopDomain") ?: ""}\"")
buildConfigField("String", "SHOPIFY_ACCESS_TOKEN", "\"${project.findProperty("shopifyAccessToken") ?: ""}\"")
```

Set them in `local.properties` (dev) or as GitHub Actions secrets (CI).

## CI/CD

`.github/workflows/ci.yml`:

1. Checkout + JDK 17 + Gradle cache
2. Generate `local.properties` from secrets
3. `./gradlew assembleDebug testDebugUnitTest --continue` — never bail early; surface every failure
4. `./gradlew lintDebug`
5. Upload test results, lint reports, and APK as artifacts
6. On push to `main` or `release/*`: build signed release APK from base64-encoded keystore secret

## Extending

### Add a new payment provider

1. Create module `payment-<provider>`
2. Implement `PaymentProvider` (6 methods + 1 flow)
3. Add `PaymentProviderId` enum value
4. Register in `AppModule.providePaymentProviders`
5. Add to `PaymentRouterConfig.enabledProviders`

No other changes — the router, checkout UI, reports, and refunds all work automatically.

### Add a new feature module

1. Create `feature-<name>` module with the build template (copy `feature-catalog/build.gradle.kts`)
2. Add to `settings.gradle.kts` and `:app`'s dependencies
3. Add a `Screen` to `ui/nav/Screen.kt`
4. Add a composable to `NavHost` in `MainActivity.kt`

### Add a new report

1. Add a state field to `ReportsState`
2. Compute it in `ReportsViewModel.load()` from `orders.recentOrders()`
3. Render in `ReportsScreen` LazyColumn

### Add a new dining mode

1. Add enum value to `DiningMode`
2. Update `FloorScreen.DiningModeGrid` to handle it
3. Update `FloorViewModel` to start the order with the new mode
4. The cart, checkout, and reports all pick it up automatically (they're dining-mode agnostic).

## Production Hardening Checklist

Before shipping to a real store:

- [ ] Replace simulated Stripe/Square SDK calls with real SDK calls (see `STRIPE_INTEGRATION.md` etc.)
- [ ] Implement `SyncBackend` against your cloud API
- [ ] Sign release builds with a real keystore
- [ ] Configure real tax rules per store via a `TaxConfiguration` admin UI
- [ ] Wire the `KeyboardWedgeScanner` into MainActivity's `dispatchKeyEvent`
- [ ] Register real printer drivers via `PrinterManager.register()`
- [ ] Configure `WorkManager` to keep sync running during sleep
- [ ] Run penetration testing on the local Room DB (SQLCipher recommended for PHI/PCI scope)
- [ ] Validate your PCI-DSS compliance path for card-present payments

---

# v2 Enterprise Extensions

This document continues from the v1 architecture and describes the enterprise-grade capabilities added in v2.

## New Domain Services

### PromotionEngine

Time-windowed promotion evaluation with day-of-week filters, category/product scope, BOGO logic, and coupon codes. Stacking is currently first-match (highest priority wins); future extension can support stacking rules.

```kotlin
val engine = PromotionEngine(promotions)
val (promo, discount) = engine.applyBest(order, now = System.currentTimeMillis(), code = "EOY10")
```

### TipPoolEngine

Computes tip distribution per shift across three pool types:

- **NONE** — servers keep their own tips (default US tipping model)
- **EVEN_SPLIT** — total tips / number of employees, with cent-level remainder handling
- **HOURS_WEIGHTED** — proportional to hours worked
- **ROLE_WEIGHTED** — proportional to role weight (server 1.0, bartender 1.2, etc. — extensible)

### AbcAnalysisEngine

Pareto classification of products by revenue contribution:
- **Class A** — top 80% of cumulative revenue (focus here)
- **Class B** — next 15% (maintain)
- **Class C** — bottom 5% (consider discontinuing)

### SplitTenderEngine

Validates and allocates a payment across multiple tenders. Fails fast on over-allocation; auto-allocates remainder to a default provider on under-allocation.

## New Audit Log

Every state-changing operation flows through `AuditLogRepository.logAction(...)`:

- `ORDER_VOIDED`, `ORDER_REFUNDED`, `ORDER_REOPENED`
- `LINE_COMPED`, `LINE_PRICE_OVERRIDE`
- `DISCOUNT_APPLIED`, `DISCOUNT_REMOVED`
- `PAYMENT_CAPTURED`, `PAYMENT_REFUNDED`, `DRAWER_OPENED`, `NO_SALE`
- `EMPLOYEE_LOGIN`, `EMPLOYEE_SWITCH`
- `INVENTORY_ADJUSTED`, `INVENTORY_TRANSFER`
- `CONFIG_CHANGED`, `SYNC_FORCE`, `SYNC_CONFLICT_RESOLVED`
- `SHIFT_OPENED`, `SHIFT_CLOSED`

Each entry includes: storeId, registerId, employeeId, employeeName, action, entityType, entityId, beforeJson, afterJson, reason, timestamp, ipAddress, deviceIdentifier.

The audit log is queryable by store, action, entity, or time range — and exportable for compliance.

## Z-Report Generation

When a shift closes, `ShiftRepository.generateZReport(shiftId)` computes:

- Gross sales (sum of paid order totals)
- Returns (from returns table)
- Discounts (sum of order-level discounts)
- Net sales (gross - returns - discounts)
- Tax collected (grouped by tax code)
- Tips collected
- Cash total (from CASH payments)
- Card total (from STRIPE/SQUARE/SHOPIFY/MANUAL)
- Other tenders (e.g., gift cards)
- Over/short (counted cash - expected cash)
- Transaction count
- Refund count, void count, no-sale count
- Employee breakdown (sales per employee during shift)

The Z-Report is persisted permanently in `z_reports` table and viewable in the Reports tab.

## Kitchen Display System

The KDS observes all open orders with kitchen-routed lines. Each ticket shows:

- Order number, table name, dining mode, guest count
- Elapsed time since order was sent, color-coded:
  - White/gray: 0-10 minutes
  - Yellow: 10-20 minutes (urgent)
  - Red: >20 minutes (critical)
  - Green: marked ready
- Items grouped by station (grill, fryer, salad, pizza, bar, etc.)
- Modifier lines and special notes prominently displayed
- Action buttons: Mark Ready → Mark Served (or Recall if needed)

Multiple stations can filter to their own view (e.g., grill sees only grill items).

## Migration System

`MigrationRepository` exposes four source-specific entry points that all funnel into a unified `MigrationJob`:

```kotlin
migrationRepo.importFromShopify(configJson, employeeId) // returns MigrationJob
migrationRepo.startJob(job.id) // begins processing
```

Each job tracks:
- Total records to process
- Records processed successfully
- Records failed
- Conflicts (with resolution state)
- Started/completed timestamps

In production, `startJob()` enqueues a WorkManager job that:
1. Pulls data from the provider's REST/GraphQL API
2. Maps each record to our domain model
3. Checks for conflicts (same SKU, email, etc.)
4. Inserts or updates the local DB
5. Enqueues sync entries to push to your cloud backend

The job is **resumable** — if interrupted, restart picks up where it left off.

## Real-Time Dashboard

`DashboardViewModel.load()` starts a 30-second auto-refresh loop that calls `AnalyticsRepository.dashboard(storeId, now)`. The dashboard computes:

- Today's sales, transactions, AOV, tips, refunds
- Yesterday's sales (for delta calculation)
- Last 7 / 30 days sales
- Active orders count
- Open tables count (from tableDao)
- Low-stock items count
- Pending sync queue count
- Hourly sales (24-hour bar chart)
- Top 10 products today
- Top 5 employees today
- Payment mix
- Dining mix
- Alerts (e.g., "Today's sales are 20% below yesterday")

Alerts are generated automatically by the analytics layer based on configurable thresholds.

## Customer 360

`CustomerDetailViewModel` aggregates everything we know about a customer:

- Profile (name, contact, birthday, address, dietary restrictions)
- Loyalty points + store credit balance
- Lifetime value (sum of all paid orders)
- Total orders, average order value
- First/last visit dates
- Top 5 favorite items by quantity
- Available loyalty rewards (filtered by points balance)
- 20 most recent orders with date and total

A server can pull up a customer's 360 view mid-order to suggest their usual, note allergies, or redeem a reward.

## Navigation

The app uses a `ModalNavigationDrawer` for full feature access (every feature in 1 tap from any screen) plus a bottom navigation bar for the 5 most-used:

**Bottom bar:** Dashboard · Tables · Menu · Reports · More

**Drawer:** Dashboard, Tables, Menu, Kitchen, Reservations, Customers, Inventory, Staff, Shift, Reports, Migrate, Settings

## Production Hardening Checklist (Updated)

Before shipping to a real store:

- [ ] Replace simulated Stripe/Square SDK calls with real SDK calls
- [ ] Implement `SyncBackend` against your cloud API
- [ ] Sign release builds with a real keystore
- [ ] Configure real tax rules per store via a `TaxConfiguration` admin UI
- [ ] Wire the `KeyboardWedgeScanner` into MainActivity's `dispatchKeyEvent`
- [ ] Register real printer drivers via `PrinterManager.register()`
- [ ] Configure `WorkManager` to keep sync running during sleep
- [ ] SQLCipher-encrypt the Room DB for PCI scope
- [ ] Validate PCI-DSS compliance path for card-present payments
- [ ] **NEW**: Wire `MigrationRepositoryImpl.startJob` to call real Shopify Admin API / Square Connect API / Stripe API
- [ ] **NEW**: Add a `TaxConfiguration` editor screen to Settings
- [ ] **NEW**: Add a `PromotionEditorScreen` for managers to create promotions
- [ ] **NEW**: Implement `InventoryManagementRepositoryImpl.observeLowStock` with a proper join query
- [ ] **NEW**: Add multi-store support (currently hardcoded `store-demo-001`)
