# Payments

## Production Status

Payments are **not fully production-ready** until the backend, Stripe Terminal SDK bridge, reader setup, tender persistence, refunds, reconciliation, and hardware/device flows are validated end to end.

Current status:

| Provider/path | Status | Production behavior |
| --- | --- | --- |
| Cash | Implemented locally | Requires ledger-grade sale, drawer, audit, shift, and reporting reconciliation before rollout. |
| Stripe Terminal | Backend/adapter scaffold plus fail-closed real mode | Debug can simulate. Release requires backend configuration and a real `StripeTerminalSdkBridge`; otherwise card-present operations fail closed. |
| Manual card | Debug-only | Disabled in release by `BuildConfig.ENABLE_MANUAL_CARD_ENTRY=false`. |
| Square | Debug/scaffold only | Not registered in release unless a real provider is implemented. Keep Square as migration source until payment SDK work is complete. |
| Shopify | Debug/scaffold only | Not registered in release unless a real provider handoff/callback flow is implemented. Keep Shopify as migration source until payment work is complete. |
| Gift card/store credit | Not production-ready | Requires backend-backed ledger before release redemption/issuance. |

## Hard Rules

- Android must never store Stripe secret keys, Square access tokens, Shopify access tokens, webhook secrets, or backend signing secrets.
- Android may store publishable/provider identifiers and backend URL only.
- Release builds must not contain fake card success paths.
- Every successful tender must be persisted as a real tender before an order is marked paid.
- Split tender must persist each tender independently; no synthetic balancing tender is allowed.
- Refunds must go back to the original tender where possible and must preserve provider identifiers.
- Payment retries must use durable idempotency keys to avoid duplicate charges.

## Stripe Terminal Flow

Production Stripe requires:

1. Backend endpoint for `/v1/terminal/connection-token`.
2. Backend endpoint for `/v1/payments/payment-intents` using Stripe secret key server-side.
3. Backend endpoint for `/v1/payments/{id}/capture` with durable idempotency.
4. Backend endpoint for `/v1/refunds` with durable idempotency.
5. Webhook ingestion and reconciliation for payment/refund state.
6. Android `StripeTerminalSdkBridge` bound to the real Stripe Terminal Android SDK.
7. Reader discovery, connect/disconnect, collect payment method, cancel, capture, and refund validation.
8. Persistence of PaymentIntent, Charge, Reader, Location, and refund identifiers.

Debug simulation remains useful for CI and local UI development, but it is not production payment processing.

## Release Fail-Closed Behavior

Release builds set:

```kotlin
ENABLE_SIMULATED_PROVIDERS = false
ENABLE_MANUAL_CARD_ENTRY = false
```

`StripePaymentProvider` now requires a real `StripeTerminalSdkBridge` in non-simulated mode. If the bridge is missing, real card-present operations fail with an explicit provider configuration error instead of returning fake reader/payment success.

Real-mode captured card metadata must come from the backend/Stripe response. The adapter no longer hardcodes Visa/4242 metadata for captured production results.

## Backend Requirements

The backend must implement and persist:

- Merchant/store/register/device isolation.
- Auth/JWT or API-key enforcement.
- Durable idempotency records keyed by merchant and operation.
- PaymentIntent creation and capture.
- Refund creation and status tracking.
- Webhook verification and reconciliation.
- Settlement/reconciliation reporting.
- Typed error responses for Android UI.

## Checkout Persistence Requirements

A production checkout is complete only when:

1. The amount due is loaded from persisted order state.
2. The payment attempt is recorded.
3. Each successful tender is persisted.
4. The order is marked paid only when amount due is exactly zero.
5. Cash tender records tendered amount and change due.
6. Card tender records provider payment identifiers.
7. Inventory decrement, receipt generation/queueing, audit events, sync outbox events, and reports are updated from the same persisted facts.

## Validation Checklist

Run these before enabling real merchant processing:

- One complete cash sale.
- One card-present Stripe test-reader sale.
- One split tender sale.
- One failed card attempt that does not mark the order paid.
- One refund to original tender.
- One void of an unpaid order.
- Retry of create/capture/refund with duplicate idempotency key.
- Shift close and Z-report reconciliation.
- Offline/pending sync visibility and retry.
