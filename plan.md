# POS Production-Ready Implementation Plan

## Phase 0 — Restore Build Health and Verification
- [x] Enable Room schema export (`exportSchema = true`)
- [x] Register `MIGRATION_2_3` in `PosMigrations.ALL`
- [ ] Fix CI workflow: separate Android and backend CI
- [ ] Ensure release signing cannot use debug keystore
- [ ] Fix any compilation errors by inspection
- [ ] Add migration tests

## Phase 1 — Build Complete POS Navigation and UI Shell
- [ ] Create reusable Compose components (PosScaffold, PosTopBar, etc.)
- [ ] Implement adaptive navigation (rail, drawer, bottom bar)
- [ ] Add complete app menu with all required screens
- [ ] Add top bar with status indicators (sync, shift, hardware)
- [ ] Implement global search/command control
- [ ] Create empty/loading/error states for all screens

## Phase 2 — Complete CRUD for All Business Entities
- [ ] Implement CRUD screens for Store/Register/Location
- [ ] Implement CRUD for Catalog (Product, Category, Variant, Modifier, Tax)
- [ ] Implement CRUD for Inventory (Stock, Adjustment, Count, Vendor, PO, Transfer, Waste)
- [ ] Implement CRUD for Customers (Customer, Loyalty, Gift Card, Store Credit)
- [ ] Implement CRUD for Employees (Employee, Role, Permission, Time Clock)
- [ ] Implement CRUD for Restaurant (Floor, Table, Reservation, Kitchen Station)
- [ ] Implement CRUD for Hardware (Printer, Drawer, Scanner, Display)
- [ ] Implement CRUD for Migration (Job, Provider, Mapping, Conflict)
- [ ] Implement CRUD for Settings (Store, Receipt, Tax, Payment, Security, Sync)

## Phase 3 — Fix Money, Tax, Discount, and Financial Truth
- [ ] Remove unsafe Double money paths
- [ ] Harden Percent rounding
- [ ] Harden Quantity fractional support
- [ ] Fix TaxEngine (preserve rules, inclusive tax guards, compound taxes)
- [ ] Fix discount permission logic and limits
- [ ] Add golden tests for all financial calculations

## Phase 4 — Complete Sale Lifecycle End-to-End
- [ ] Wire product taps to active cart
- [ ] Wire barcode scan to cart
- [ ] Persist cart updates
- [ ] Implement modifier selection
- [ ] Implement discount with permission/override
- [ ] Implement customer attachment
- [ ] Fix checkout to load actual amount due
- [ ] Implement payment persistence
- [ ] Implement split tender (no synthetic truth)
- [ ] Implement gift card/store credit tender
- [ ] Implement cash tender with change
- [ ] Implement card provider payment ID persistence
- [ ] Implement inventory decrement on paid order
- [ ] Implement receipt generation and queue
- [ ] Implement audit and sync outbox on sale
- [ ] Implement refund workflow
- [ ] Implement void workflow

## Phase 5 — Real Stripe Terminal Integration
- [ ] Implement `StripeTerminalSdkBridge` real implementation
- [ ] Ensure `sdkBridge` is never null in release when configured
- [ ] Implement connection token provider via backend
- [ ] Implement reader discovery/connect/disconnect
- [ ] Implement collect payment method
- [ ] Implement process/capture flow
- [ ] Implement cancellation and refunds
- [ ] Persist real Stripe identifiers
- [ ] Remove hardcoded card metadata in real mode
- [ ] Ensure simulated readers only in debug

## Phase 6 — Payment Providers, Cash, Refunds, Voids, Reconciliation
- [ ] Implement cash provider with drawer movement
- [ ] Disable Square/Shopify as payment providers in release if not real
- [ ] Implement refunds through original tender
- [ ] Implement voids with reason and audit
- [ ] Implement payment reconciliation

## Phase 7 — Sync and Backend Completion
- [ ] Remove/migrate old SyncQueueEntity
- [ ] Unify all repositories to write SyncOutboxEntity
- [ ] SyncEngine drains actual outbox queue
- [ ] Implement backend sync routes (no 501)
- [ ] Implement backend idempotency
- [ ] Implement conflict resolution
- [ ] Add immediate sync after critical events
- [ ] Add offline indicator and pending sync count
- [ ] Add sync diagnostics screen

## Phase 8 — Inventory and Purchasing Completion
- [ ] Decrement inventory on paid sale
- [ ] Increment on refund with restock
- [ ] Stock adjustment workflow
- [ ] Stock count workflow (start, count, review, approve)
- [ ] Vendor CRUD
- [ ] Purchase order CRUD
- [ ] Receiving workflow
- [ ] Reorder rules
- [ ] Low-stock dashboard
- [ ] Transfers between locations
- [ ] Waste/spoilage
- [ ] Inventory valuation report
- [ ] Barcode support

## Phase 9 — Hardware Completion
- [ ] Network printer (socket/ESC-POS)
- [ ] USB printer with permission flow
- [ ] Bluetooth printer
- [ ] Receipt queue with retry
- [ ] Kitchen printer routing
- [ ] Cash drawer with audit
- [ ] Barcode scanner key event integration
- [ ] Customer display state stream
- [ ] Hardware diagnostics screen

## Phase 10 — Restaurant, KDS, Reservations
- [ ] Harden table lifecycle (available → seated → ordered → paid → cleaning)
- [ ] Server assignment and guest count
- [ ] Send-to-kitchen flow
- [ ] KDS ticket states (new, in-progress, ready, completed, recalled)
- [ ] Kitchen printing
- [ ] Reservations with availability/conflict model
- [ ] No-show/cancel/deposit hooks

## Phase 11 — Customers, Loyalty, Gift Cards, CRM
- [ ] Customer CRUD with purchase history
- [ ] Customer notes and tax exemption
- [ ] Consent tracking
- [ ] Loyalty rules, accrual, redemption, expiration
- [ ] Gift card backend-backed ledger (or disable in release)
- [ ] Store credit ledger
- [ ] Reconciliation report

## Phase 12 — Reports, Dashboard, Shifts, Z-Reports
- [ ] Real dashboard metrics (today gross, net, tax, tips, refunds, AOV)
- [ ] Sales summary, tender summary, tax liability
- [ ] Product/category/employee reports
- [ ] Z-report with all required fields
- [ ] Shift open/close with blind close
- [ ] Cash drawer reconciliation
- [ ] Payment reconciliation

## Phase 13 — Migration Center Completion
- [ ] Real migration backend routes (no 501)
- [ ] Shopify/Square OAuth with token vault
- [ ] CSV import
- [ ] Dry-run, mapping, conflict resolution
- [ ] Final reconciliation and cutover

## Phase 14 — Security, Auth, Permissions, Audit
- [ ] Employee login with hashed PIN and lockout
- [ ] Sessions (employee, register, device)
- [ ] Device registration
- [ ] Manager override with reason and audit
- [ ] Domain-level permission enforcement
- [ ] Tamper-resistant audit logs
- [ ] Remove unnecessary permissions
- [ ] Security documentation

## Phase 15 — Settings and Onboarding
- [ ] First-run onboarding flow
- [ ] Store/register/device configuration
- [ ] Admin employee creation
- [ ] Tax, payment, receipt, hardware setup
- [ ] Test sale and first shift
- [ ] Production readiness gate

## Phase 16 — Tests
- [ ] Money, Percent, Quantity tests
- [ ] Tax, discount, order total tests
- [ ] Cart, checkout, payment tests
- [ ] Refund, void, inventory tests
- [ ] Sync, migration, permission tests
- [ ] Hardware failure tests
- [ ] Z-report reconciliation tests

## Phase 17 — Documentation
- [ ] Update all docs with honest status labels
- [ ] README, ARCHITECTURE, SECURITY, PAYMENTS, etc.
- [ ] Final verification checklist
