# EnterprisePOS Changelog

All notable changes to EnterprisePOS are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive test suite for core, domain, data, payment, hardware, and app modules
- Simulated hardware managers for testing without physical devices
- Integration tests for database migrations and repository chains
- UI tests for Compose components and screen flows
- Documentation for architecture, setup, deployment, API, hardware, security, testing, and troubleshooting
- Encryption manager with AES-256-GCM support
- Audit logger and session manager for compliance
- Promotion engine with BOGO, code-based, and priority-based discounts
- Split tender and tip pool engines
- Hardware abstraction layer for printer, drawer, scanner, and display

### Changed
- Migrated from Kapt to KSP for annotation processing
- Updated Gradle to 8.2 with Kotlin DSL
- Increased minSdk to 26 (Android 8.0)
- Upgraded Stripe Terminal SDK to 3.0.0

### Fixed
- Race condition in sync outbox processing
- Memory leak in OrderRepositoryImpl coroutine scope
- Tax calculation precision for compound rates
- Offline payment queue overflow handling

### Security
- Implemented Argon2id PIN hashing
- Added certificate pinning for API communication
- Enabled hardware-backed keystore when available
- Added root detection and tamper detection
- Implemented progressive login lockout

## [1.2.0] - 2024-03-15

### Added
- Multi-store support with store switching
- Kitchen display system (KDS) integration
- Customer loyalty program
- Offline mode for payment processing
- Support for Stripe Terminal WisePad 3

### Changed
- Improved checkout flow with split tender UI
- Enhanced reporting with real-time analytics
- Optimized database queries for large catalogs

### Fixed
- Fixed issue where discounts were not applied to modifiers
- Corrected tax calculation for order-level discounts
- Resolved crash when scanning unsupported barcode formats
- Fixed memory leak in MainActivity ViewModel

### Deprecated
- Legacy Bluetooth printer API (replaced by unified hardware manager)
- Old sync endpoint v1 (replaced by v2 with conflict resolution)

## [1.1.0] - 2024-01-20

### Added
- Employee time tracking and payroll export
- Inventory management with low stock alerts
- Purchase order generation
- Vendor management
- Support for Epson DM-D30 customer display

### Changed
- Refactored payment module to support multiple providers
- Updated UI to Material Design 3
- Improved startup time by 40%

### Fixed
- Fixed race condition in concurrent order modification
- Corrected receipt formatting for multi-currency
- Resolved issue with cash drawer not opening on some devices

## [1.0.0] - 2023-11-01

### Added
- Initial release of EnterprisePOS
- Core POS functionality: orders, payments, catalog, employees
- Stripe Terminal integration for card-present payments
- ESC/POS thermal printer support
- Basic reporting and analytics
- Role-based access control
- Audit logging
- Offline mode with sync queue

### Changed
- N/A (initial release)

### Fixed
- N/A (initial release)

## Migration Guide

### 1.1 to 1.2

1. Update Stripe Terminal SDK to 3.0.0
2. Run database migration: `PosDatabase` version 3 to 4
3. Update hardware configuration to new unified format
4. Replace deprecated `BluetoothPrinterManager` with `HardwareManagerFactory`

### 1.0 to 1.1

1. Run database migration: `PosDatabase` version 2 to 3
2. Configure inventory settings in admin panel
3. Enable employee time tracking if needed
4. Update API base URL to v2 endpoints

## Upcoming

### 1.3.0 (Planned Q2 2024)

- Table management for full-service restaurants
- Reservation system integration
- Mobile ordering (customer-facing app)
- Advanced analytics with AI-powered insights
- Multi-language support
- Cloud-based backup and restore
