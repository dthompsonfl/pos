# EnterprisePOS Architecture

## Overview

EnterprisePOS is a modular Android point-of-sale system designed for multi-store restaurant chains and enterprise retail environments. The application is built using a clean, layered architecture with clear separation of concerns.

## Module Structure

```
EnterprisePOS/
├── core/                    # Core domain types, value objects, and utilities
├── domain/                  # Business logic, use cases, and domain engines
├── data/                    # Data persistence, repositories, and sync
├── payment-stripe/          # Stripe Terminal payment provider
├── hardware/                # Hardware abstraction (printer, drawer, scanner, display)
├── app/                     # Android application, UI, and ViewModels
└── docs/                    # Documentation
```

## Core Module

The `core` module contains domain-agnostic types and utilities:

- **Value Objects**: `Money`, `Percent`, `Quantity` — immutable, validated types for financial calculations
- **Type Wrappers**: `OrderId`, `StoreId`, `ProductId`, `EmployeeId` — type-safe identifiers
- **Encryption**: `EncryptionManager` — pure JVM AES-256-GCM encryption for sensitive data
- **Logging**: `PosLogger` interface with `NoopLogger` for testing

## Domain Module

The `domain` module encapsulates all business logic:

### Engines
- **CartEngine**: Manages shopping cart state, line items, modifiers, and quantity changes
- **TaxEngine**: Calculates taxes with support for standard, compound, exempt, and category-specific rates
- **PromotionEngine**: Applies discounts, BOGO deals, and coupon-based promotions with priority ordering
- **SplitTenderEngine**: Handles multiple payment methods for a single transaction
- **TipPoolEngine**: Distributes tips among employees based on configurable rules

### Security
- **PermissionChecker**: Role-based access control (MANAGER, CASHIER, KITCHEN, ADMIN)
- **AuditLogger**: Immutable audit trail for compliance and dispute resolution
- **SessionManager**: Employee session management with timeout and validation
- **PinHasher**: Argon2-based PIN hashing for employee authentication
- **LoginAttemptLimiter**: Brute-force protection with progressive lockout

## Data Module

The `data` module provides persistence and synchronization:

- **Room Database**: Local SQLite database with type converters and migrations
- **Repositories**: Concrete implementations of domain repository interfaces
- **Sync Outbox**: Reliable offline-first sync with conflict resolution
- **Migrations**: Versioned schema migrations (2 to 3, 3 to 4, 4 to 5)

## Payment Module

The `payment-stripe` module integrates Stripe Terminal:

- **StripeTerminalPaymentProvider**: Card-present payments, refunds, and offline retry
- **StripePaymentModelMapper**: Converts between Stripe SDK models and domain models
- **Offline Support**: Queues refunds for retry when connectivity is restored

## Hardware Module

The `hardware` module abstracts peripheral devices:

- **Printer**: ESC/POS thermal receipt printing, kitchen chits, and reports
- **Cash Drawer**: Drawer state management and open control
- **Barcode Scanner**: Scan event collection with format support
- **Customer Display**: Running total, item, and thank-you messages

## App Module

The `app` module contains Android-specific code:

- **Activities**: `MainActivity`, `OnboardingActivity`
- **ViewModels**: Hilt-injected ViewModels with saved state
- **Compose UI**: Jetpack Compose screens, components, and themes
- **Navigation**: Bottom navigation with screen-level routing

## Dependency Flow

```
app -> domain -> core
app -> data -> domain -> core
app -> payment-stripe -> domain -> core
app -> hardware -> domain -> core
```

No module may depend on a module to its right. The `core` module has no internal dependencies.

## Testing Strategy

- **Unit Tests**: JVM tests for core, domain, and pure logic (JUnit 4, MockK, Truth, Turbine)
- **Integration Tests**: Android instrumented tests for Room database, repository chains, and end-to-end flows
- **UI Tests**: Compose UI tests with `createComposeRule` for component verification and screen flows
- **Hardware Tests**: Simulated manager tests for peripheral device logic

## Build System

- **Gradle**: Kotlin DSL with version catalogs
- **KSP**: Kotlin Symbol Processing for Room and other annotation processors
- **Hilt**: Dependency injection with `hilt-android` and `hilt-compiler`
- **Min SDK**: 26 (Android 8.0)
- **Compile SDK**: 34 (Android 14)
- **Java**: 17
- **Kotlin**: 1.9
