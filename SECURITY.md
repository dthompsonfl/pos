# Security

## Secret handling

**NEVER stored on the Android device:**
- Stripe secret key (`sk_live_*` / `sk_test_*`)
- Shopify access token (`shpat_*`)
- Square access token (`EAAA*`)
- Backend admin credentials
- Any provider OAuth refresh tokens

**Server-side only (backend environment variables):**
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `SHOPIFY_CLIENT_SECRET`
- `SQUARE_APPLICATION_SECRET`
- `JWT_SECRET`
- `POS_API_KEY`
- `DATABASE_URL` (with credentials)

**OK to embed in APK (not secrets):**
- `STRIPE_LOCATION_ID` — published location identifier
- `SQUARE_APPLICATION_ID` — published app identifier
- `SHOPIFY_SHOP_DOMAIN` — public shop URL
- `BACKEND_BASE_URL` — public API URL

The Android app talks ONLY to your backend. The backend talks to Stripe/Shopify/Square. The APK contains no secrets even if reverse-engineered.

## PIN hashing

Employee PINs are stored as PBKDF2-HMAC-SHA256 hashes with:
- 100,000 iterations (slows brute-force)
- 16-byte random salt per PIN
- Constant-time comparison (prevents timing attacks)

Format: `pbkdf2$<iterations>$<saltHex>$<hashHex>`

Raw PINs NEVER appear in:
- The database
- Seed files (only in code comments documenting demo PINs)
- Logs
- UI state
- Network traffic

Failed-login lockout: 5 failed attempts within 15 minutes → 5-minute lockout per employee. Successful login resets the counter.

## Local storage

The following are stored locally in the Room database:
- Orders, order lines, payments (with provider transaction IDs, NOT card numbers)
- Customers (name, contact, loyalty points, dietary restrictions)
- Employees (name, role, PIN hash, login attempts)
- Inventory snapshots and adjustments
- Audit log entries
- Sync outbox events
- Gift card redemptions (but not gift card balances for production — see below)

**NEVER stored locally:**
- Raw card numbers (PCI scope)
- Card verification codes (CVV)
- Track data from magnetic stripes
- Provider secret keys
- Provider OAuth tokens

## Backup policy

`android:allowBackup="false"` in the manifest. `dataExtractionRules` and `fullBackupContent` exclude all domains as defense-in-depth.

**Why:** POS data includes payment records, employee PIN hashes, customer PII, and audit logs. Cloud backup could expose this data if the user's Google account is compromised.

**Device loss response:** If a device is lost, the merchant should:
1. Revoke the device's POS API key from the backend
2. Force a remote wipe via MDM (if available)
3. Review audit logs from the device's last known sync
4. Rotate any employee PINs that may have been entered on the device

## Payment scope (PCI DSS)

This app is designed to stay OUT of PCI DSS scope:

- No raw cardholder data is ever collected, stored, or transmitted by the app
- Card-present payments go through the Stripe Terminal SDK which is a validated SPoC solution
- Manual card entry is DISABLED in release builds (`ENABLE_MANUAL_CARD_ENTRY = false`)
- The Stripe Terminal SDK handles all card data within its certified boundary
- Refunds reference the original Stripe charge ID — no card data needed

If you enable manual card entry in debug for testing, NEVER enable it in production. Doing so would put you in PCI DSS SAQ-D scope.

## Database migrations

- `fallbackToDestructiveMigration()` is REMOVED from production
- All schema changes use explicit `Migration` objects in `PosMigrations.kt`
- Each migration uses `ALTER TABLE ... ADD COLUMN` (never `DROP TABLE`)
- Migration tests should be added alongside each migration

## Network security

- `network_security_config.xml` enforces HTTPS in production
- Cleartext traffic is denied in release
- Debug builds allow user-installed CAs for local backend testing only

## Audit log

Every state-changing operation writes an `AuditLogEntry`:
- `actor` (employeeId + employeeName)
- `approver` (managerId, if manager override was used)
- `action` (one of 50+ `AuditAction` enum values)
- `entityType` + `entityId`
- `beforeJson` / `afterJson` (snapshot of state change)
- `reason` (e.g., void reason, refund reason)
- `registerId`, `storeId`
- `timestamp`
- `deviceIdentifier`

Audit logs are:
- Immutable (no UPDATE or DELETE queries on `audit_log` table)
- Synced to the backend via the outbox
- Exportable for compliance audits

## Gift cards

Local-only gift card balances are NOT safe for production because:
- A factory reset wipes the balance
- A user could downgrade the app to revert redemptions
- Multi-device merchants would see inconsistent balances

**Production requirement:** Gift card balances must be backed by a server-side ledger. The Android app should call the backend for every issue/reload/redeem and never trust a local balance. The current `GiftCardRepository` is suitable for development only and must be replaced with `RemoteGiftCardRepository` before production rollout.
