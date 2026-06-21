# Security & Lint Fix Plan

## Stage 1 — Manifest & Build Configuration
- Fix AndroidManifest.xml (WorkManagerInitializer removal, autoVerify fix)
- Update app/build.gradle.kts (compileSdk/targetSdk to 35, add -print* proguard directives)
- Update libs.versions.toml (remove unused shopify/square-reader, bump safe versions)
- Verify hardware/build.gradle.kts (mlkit-barcode-scanning, security, biometric)

## Stage 2 — Security Hardening
- Replace android.util.Log in RouteValidator.kt with project Logger
- Sanitize MigrationRoutes.kt logging (remove sensitive response bodies)
- Verify core Logger interface and NoopLogger
- Fix AuditLogRepositoryImpl.kt (remove TODO-like comment, replace android.util.Log with Logger)

## Stage 3 — Compose Modifier Ordering
- Fix BottomNavigationBar modifier position in AdaptiveNavigation.kt
- Fix PosTopAppBar modifier position in AdaptiveNavigation.kt

## Stage 4 — Locale & Date Format Fixes
- Search for implied locale date formats across settings screens and AdaptiveNavigation.kt
- Fix with explicit locale or DateTimeFormatter

## Stage 5 — OptIn / Suppression Annotations
- Search for files requiring @OptIn across feature-settings and payment modules
- Add proper annotations with justification comments

## Stage 6 — Empty Methods & Dead Code
- Fix empty methods in BarcodeScanner.kt, HardwareSettingsViewModel.kt, RegisterSettingsViewModel.kt, TokenVault.kt
- Remove or implement with TODO comment replaced by proper action

## Stage 7 — Test Fixes
- Search for JUnit malformed declarations and platform types in test files
- Fix explicit types where needed

## Stage 8 — General Lint Fixes
- Search for: redundant suppression, unused imports, redundant suspend, unmodified var, string concat, if-null foldable, sequence optimization, range check, Enum.values, private property names with underscores, gray vs grey
- Fix each category systematically

## Stage 9 — Specific Gaps
- EnterpriseRepositoryImpls.kt line 779: replace UnsupportedOperationException with Result.failure
- Verify core/data build files have androidx.security and androidx.biometric

## Stage 10 — Report
- Compile final report of all fixes, categories, and skipped items
