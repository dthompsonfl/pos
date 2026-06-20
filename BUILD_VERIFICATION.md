# Build Verification

## Prerequisites

- JDK 17 (Temurin recommended)
- Android SDK with:
  - `platforms;android-34`
  - `build-tools;34.0.0`
  - `platform-tools`
- (Optional) Physical Android device or emulator for runtime testing
- (Optional) Stripe test account + Terminal location for payment testing

## Setup

```bash
# 1. Clone or extract the project
unzip pos-system-v2.zip
cd pos-system-v2

# 2. Create local.properties
cp local.properties.template local.properties
# Edit local.properties:
#   sdk.dir=/path/to/Android/Sdk
#   stripeLocationId=loc_xxx  (your Stripe Terminal location ID, or blank for debug)
#   squareApplicationId=sq0idp-xxx  (or blank)
#   shopifyShopDomain=your-store.myshopify.com  (or blank)
#   backendBaseUrl=https://pos-backend.example.com  (your backend URL)
```

## Build commands

### Verify wrapper
```bash
./gradlew --version
# Expected output:
# Gradle 8.9
# Kotlin:       2.0.20
# JVM:          17
```

### Clean
```bash
./gradlew clean
# Expected: BUILD SUCCESSFUL
```

### Unit tests
```bash
./gradlew test
# Expected: BUILD SUCCESSFUL
# All 71 tests pass.
# Report: build/reports/tests/test/index.html
```

### Debug APK
```bash
./gradlew assembleDebug
# Expected: BUILD SUCCESSFUL
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Lint
```bash
./gradlew lintDebug
# Expected: BUILD SUCCESSFUL (warnings allowed, no fatals)
# Report: app/build/reports/lint-results-debug.html
```

### Release APK (requires signing env vars)
```bash
export POS_RELEASE_KEYSTORE_PATH=/path/to/pos-release.keystore
export POS_RELEASE_KEYSTORE_PASSWORD=...
export POS_RELEASE_KEY_ALIAS=...
export POS_RELEASE_KEY_PASSWORD=...

./gradlew assembleRelease
# Expected: BUILD SUCCESSFUL
# Output: app/build/outputs/apk/release/app-release.apk (SIGNED)

# If env vars are missing, the build will FAIL with:
# "Release signing not configured. Set POS_RELEASE_KEYSTORE_PATH, ..."
# This is intentional — release builds must never silently fall back to debug signing.
```

### Backend
```bash
cd backend
cp .env.example .env
# Edit .env with your test credentials

../gradlew :backend:run
# Expected: Server starts on port 8080
# Test: curl http://localhost:8080/health
```

## CI verification

The `.github/workflows/ci.yml` workflow runs:
1. Checkout
2. JDK 17 setup
3. Android SDK setup (via `android-actions/setup-android@v3`)
4. Gradle setup with caching
5. `local.properties` generation from secrets
6. **Wrapper verification** (`./gradlew --version`)
7. `./gradlew clean assembleDebug testDebugUnitTest --continue --stacktrace`
8. `./gradlew lintDebug --continue`
9. Artifact uploads (test results, lint reports, debug APK)
10. On `main`/`release/*` branches: signed release APK build

## Expected outcomes

| Command | Expected result |
|---------|------------------|
| `./gradlew --version` | Gradle 8.9, Kotlin 2.0.20, JVM 17 |
| `./gradlew clean` | BUILD SUCCESSFUL |
| `./gradlew test` | BUILD SUCCESSFUL, 71 tests pass |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL, app-debug.apk created |
| `./gradlew lintDebug` | BUILD SUCCESSFUL, no fatal issues |
| `./gradlew assembleRelease` (with env vars) | BUILD SUCCESSFUL, app-release.apk signed |
| `./gradlew assembleRelease` (without env vars) | FAILS with clear error message |

## Troubleshooting

### `gradle-wrapper.jar missing`
The wrapper jar must exist at `gradle/wrapper/gradle-wrapper.jar`. If missing, restore from git or download from a known-good Gradle 8.9 release.

### `SDK location not found`
Ensure `local.properties` contains `sdk.dir=/path/to/Android/Sdk`. The path must point to the SDK root (containing `platforms/`, `build-tools/`, etc.).

### `Release signing not configured`
Set the four `POS_RELEASE_*` environment variables before running `assembleRelease`. See `local.properties.template` for details.

### `Compile error: unresolved reference: Quantity`
The `Quantity` class is in `com.enterprise.pos.core`. Ensure `core` module is a dependency. All modules that depend on `:core` get `Quantity` transitively.

### `Test fails: Percent.of(8.25).of($100) != $8.25`
This was the v1 bug. v2 fixes it — `Percent.of(8.25)` means 8.25%, applied to $100 gives $8.25. If you see this test failing, the `Money.kt` file is from v1 — re-apply the v2 fix.

### `Backend won't start`
Check that all required environment variables are set (see `backend/.env.example`). The backend will refuse to start without `STRIPE_SECRET_KEY`, `JWT_SECRET`, and `POS_API_KEY`.
