# Payments

## Architecture

The Android app NEVER holds Stripe secret keys. All payment operations flow through your backend, which holds the secrets in environment variables.

```
┌──────────────────┐      HTTPS      ┌──────────────────┐      Stripe API      ┌─────────┐
│   Android POS    │ ──────────────> │   Your Backend   │ ───────────────────> │ Stripe  │
│                  │                 │  (server-side)   │                      │         │
│ Stripe Terminal  │                 │  Holds:          │                      │ Issues: │
│ SDK (reader I/O) │                 │  - sk_live_*     │                      │ - PI    │
│                  │                 │  - webhook secret│                      │ - Charge│
│                  │                 │  - JWT secret    │                      │ - Refund│
└──────────────────┘                 └──────────────────┘                      └─────────┘
```

## Stripe Terminal flow

### 1. Initialize

App startup:
```kotlin
// In PosApplication.onCreate, after Hilt init:
val router: PaymentRouter = ...
router.initializeAll() // calls StripePaymentProvider.initialize()
```

`StripePaymentProvider.initialize()`:
1. Reads `BACKEND_BASE_URL` from BuildConfig
2. POSTs to `/v1/terminal/connection-token` (with POS API key auth)
3. Backend calls `StripeService.createConnectionToken()` → returns `secret`
4. App passes `secret` to `StripeTerminalSdkBridge.initialize()`
5. Bridge calls `Terminal.init(context, config, tokenProvider, listener)`

### 2. Discover readers

`StripePaymentProvider.discoverReaders()`:
- Debug (simulate=true): returns a fake reader list
- Release: calls `Terminal.discoverReaders(DiscoveryConfiguration(...), listener)`

### 3. Connect reader

`StripePaymentProvider.connectReader(reader)`:
- Bridge calls `Terminal.connectReader(ConnectionConfiguration.BluetoothConnectionConfiguration(reader.serial))`

### 4. Create PaymentIntent

`StripePaymentProvider.createPaymentIntent(request)`:
- POST `/v1/payments/payment-intents` with `amountMinor`, `currency`, `metadata`
- Backend calls `StripeService.createPaymentIntent()`:
  - `PaymentIntentCreateParams.builder().setAmount(...).setCurrency(...).setPaymentMethodTypes(listOf("card_present")).setCaptureMethod(MANUAL)`
  - Server-side idempotency key (UUID per request) so duplicate requests don't double-create
- Returns `{id, clientSecret, amount, currency}` to the app
- App wraps as `PaymentIntentHandle(provider=STRIPE, intentId=pi_..., secret=...)`

### 5. Collect payment method

`StripePaymentProvider.collectPayment(handle, events)`:
- Bridge calls `Terminal.collectPaymentMethod(intent, listener)`
- Listener forwards events: `InsertCard`, `ReadingCard`, `Processing`
- Returns updated PaymentIntent with `payment_method` populated

### 6. Capture

- App POSTs to `/v1/payments/{id}/capture` (with optional tip)
- Backend calls `StripeService.capturePaymentIntent()`:
  - `PaymentIntent.retrieve(id).capture(CaptureParams)`
  - Server-side idempotency key
- Returns captured PaymentIntent with `status: "succeeded"`

### 7. Persist & sync

App receives `PaymentResult`, calls `OrderRepository.markPaid(orderId, payment, employeeId)`:
- Persists `PaymentEntity`
- Marks order PAID if `amountDue == 0`
- Writes audit log (`PAYMENT_CAPTURED`, `ORDER_PAID`)
- Enqueues sync outbox events

### 8. Refund

`PaymentRouter.refund(paymentId, originalProvider, amount, reason)`:
- App POSTs to `/v1/refunds` with `paymentIntentId`, `amountMinor`, `reason`
- Backend calls `StripeService.refund()`:
  - `RefundCreateParams.builder().setPaymentIntent(piId).setAmount(...).setReason(...)`
  - Idempotency key
- Returns refund ID + status

## Idempotency

Every backend endpoint that creates state uses an idempotency key:
- Connection token: `UUID.randomUUID()` per request (short-lived anyway)
- PaymentIntent creation: `Idempotency-Key` header
- Capture: `Idempotency-Key` header
- Refund: `Idempotency-Key` header
- Sync events: `idempotencyKey` field in payload

This means if the app retries due to network flakiness, the server recognizes the duplicate and returns the original result instead of creating a second payment.

## Test mode vs production

- Debug builds: `BuildConfig.ENABLE_SIMULATED_PROVIDERS = true`
  - StripePaymentProvider uses `simulate = true`
  - No real HTTP calls; no real reader needed
  - Useful for development and CI
- Release builds: `BuildConfig.ENABLE_SIMULATED_PROVIDERS = false`
  - Real HTTP calls to backend
  - Real Stripe Terminal SDK calls
  - Real reader required for card-present

## Offline mode

Stripe Terminal supports offline payment queueing for up to 9 days. To enable:

1. Configure `OfflineConfiguration` in `TerminalConfiguration` (in `StripeTerminalSdkBridge.initialize`)
2. Set `PaymentCapability.OFFLINE_MODE` in `StripePaymentProvider.capabilities`
3. The router's `DefaultRoutingPolicy` checks `allowsOffline` per payment
4. Offline payments are stored locally and uploaded when network returns
5. Backend reconciles offline payments via Stripe webhook events

**Limitations:**
- Offline payments are capped at a per-merchant limit set by Stripe
- High-risk transactions may be declined when uploaded
- Refunds cannot be processed offline

## Tap to Pay on Android

If your device supports Tap to Pay (Pixel 7+, Samsung Galaxy S22+):
1. Add `com.stripe:stripeterminal-taptopay` dependency
2. Configure `TerminalConfiguration` with `tapToPayConfiguration`
3. Discover readers will return a built-in TTP reader
4. Connect and collect like any other reader

## Connected accounts (multi-merchant)

If your backend serves multiple merchants via Stripe Connect:
1. Pass `stripeAccount` (the connected account ID, `acct_...`) in `RequestOptions`
2. Backend logs the merchant context in audit trail
3. Each merchant's payments are isolated to their Stripe account
4. The Android app sends the merchant ID in the `Authorization` header alongside the POS API key
