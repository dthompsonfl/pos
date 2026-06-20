# Sync Architecture

## Overview

The POS is offline-first. Every write goes to the local Room DB immediately, and a sync outbox event is enqueued in the SAME transaction. A WorkManager job drains the outbox in the background and POSTs events to your backend with idempotency keys.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Android POS                                                            │
│                                                                        │
│  ┌─────────────┐    ┌─────────────┐    ┌──────────────────────────┐   │
│  │  Order DB   │ →  │  Outbox     │ →  │  WorkManager (15min)     │   │
│  │  (Room)     │    │  (Room)     │    │  + foreground trigger    │   │
│  └─────────────┘    └─────────────┘    └──────────────────────────┘   │
│                                                │                       │
└────────────────────────────────────────────────┼───────────────────────┘
                                                 │ HTTPS POST
                                                 │ Idempotency-Key: <uuid>
                                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Your Backend                                                           │
│                                                                        │
│  POST /v1/sync/events                                                  │
│   1. Check idempotencyKey in Redis/DB                                  │
│   2. If seen → return 410 Duplicate with original eventId              │
│   3. Validate schema version                                           │
│   4. Apply to backend state                                            │
│   5. Return 200 Accepted                                               │
│   6. On conflict → 409 Conflict with serverVersionJson                 │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

## Outbox event model

Every event is stored in `sync_outbox` table:

| Field | Type | Purpose |
|-------|------|---------|
| `id` | TEXT (UUID) | Primary key |
| `storeId` | TEXT | Tenant scoping |
| `registerId` | TEXT? | Source register |
| `employeeId` | TEXT? | Actor |
| `entityType` | TEXT | `orders` / `payments` / `inventory` / etc. |
| `entityId` | TEXT | The entity's primary key |
| `operation` | TEXT | `UPSERT` / `DELETE` |
| `schemaVersion` | INT | For backward compat |
| `idempotencyKey` | TEXT (UUID, UNIQUE) | Server dedup |
| `payloadJson` | TEXT | Serialized snapshot |
| `createdAt` | INTEGER | When enqueued |
| `attemptCount` | INTEGER | Failed retry count |
| `nextAttemptAt` | INTEGER | Backoff time |
| `lastError` | TEXT? | Last error message |
| `status` | TEXT | `PENDING` / `IN_FLIGHT` / `ACKNOWLEDGED` / `FAILED` / `CONFLICT` |

## Retry & backoff

Failed events retry with exponential backoff:
- Attempt 1: 30 seconds
- Attempt 2: 60 seconds
- Attempt 3: 120 seconds
- Attempt 4: 240 seconds
- Attempt 5: 480 seconds
- Attempt 6+: 1800 seconds (30 min cap)

After 10 attempts, the event stays in `FAILED` status and surfaces to a manager UI for manual resolution.

## Conflict resolution

When the server returns `409 Conflict`, the event is marked `CONFLICT` and surfaced to the UI. The merchant chooses:
- **KEEP_LOCAL** — overwrite server with local version
- **KEEP_REMOTE** — discard local version
- **MERGE** — apply field-by-field merge (server-defined)
- **SKIP** — discard both versions

The resolution is POSTed to `/v1/sync/events/{id}/resolve` with the chosen strategy.

## Sync status UI

The dashboard shows:
- Pending event count (live `Flow<Int>`)
- Conflict count
- Last successful sync time

When offline, the dashboard shows an "Offline" banner. Sales continue to work — they're just queued.

## Atomic enqueue

The outbox enqueue happens in the SAME Room transaction as the data write. This guarantees:

- If the data write commits, the sync event is queued
- If the data write rolls back, no orphan sync event is created
- The order cannot be "paid locally but lost on sync"

Implementation: `OrderRepositoryImpl.markPaid()` calls `orderDao.upsert()` and `syncDao.enqueue()` within a single `@Transaction` (or coroutine context that wraps both in a Room transaction).

## Backend contract

Your backend MUST implement:

```http
POST /v1/sync/events
Content-Type: application/json
Authorization: Bearer <POS_API_KEY>
Idempotency-Key: <uuid>

{
  "eventId": "...",
  "storeId": "...",
  "entityType": "orders",
  "entityId": "...",
  "operation": "UPSERT",
  "schemaVersion": 1,
  "idempotencyKey": "<uuid>",
  "payloadJson": "{...serialized entity...}",
  "createdAt": 1718800000000
}
```

Responses:
- `200` / `201` — Accepted (event deleted from outbox)
- `410 Gone` — Duplicate (event deleted from outbox)
- `409 Conflict` — Server has a different version (event marked CONFLICT)
- `422 Unprocessable Entity` — Permanent rejection (event marked FAILED)
- `5xx` / network error — Retried with backoff

## Manual sync trigger

The dashboard's refresh button calls `SyncEngine.drainBatch()` directly for immediate sync. This is useful after a network outage ends.

## What gets synced

| Entity | Triggers |
|--------|----------|
| orders | create, update, status change, markPaid, void |
| order_lines | add, remove, qty change, discount |
| payments | capture, refund |
| customers | create, update, loyalty points, store credit |
| inventory | adjustment, transfer, reorder |
| shifts | open, close, Z-report |
| audit_log | every audit event |
| reservations | create, confirm, seat, cancel |
| gift_card_transactions | issue, reload, redeem |
| migration_jobs | create, start, complete |

## What is NOT synced

- Local-only settings (theme, auto-print, etc.) — stored in `settings` table, not synced
- Reader connection state — ephemeral
- Computed reports — derived from synced data on the backend
