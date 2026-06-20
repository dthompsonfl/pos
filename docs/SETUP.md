# EnterprisePOS Setup Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or higher
- Android SDK with API 34
- Git

## Clone the Repository

```bash
git clone https://github.com/enterprise/pos-android.git
cd pos-android
```

## Configure Environment

### 1. Local Properties

Create `local.properties` in the project root:

```properties
sdk.dir=/path/to/Android/Sdk
```

### 2. Stripe Configuration

Add Stripe keys to `local.properties`:

```properties
STRIPE_API_KEY=pk_test_...
STRIPE_TERMINAL_KEY=pk_test_...
```

### 3. Signing Configuration

Create `keystore.properties` for release builds:

```properties
STORE_FILE=/path/to/keystore.jks
STORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

## Build the Project

### Debug Build

```bash
./gradlew :app:assembleDebug
```

### Release Build

```bash
./gradlew :app:assembleRelease
```

## Run Tests

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

### Single Module Tests

```bash
./gradlew :domain:test
./gradlew :data:test
./gradlew :hardware:test
./gradlew :payment-stripe:test
```

## Install on Device

### Debug APK

```bash
./gradlew :app:installDebug
```

### Release APK

```bash
./gradlew :app:installRelease
```

## IDE Configuration

### Android Studio Settings

1. Enable **File > Settings > Editor > General > Auto Import** for Kotlin
2. Set **Editor > Code Style > Kotlin** to use official Kotlin style
3. Enable **Build > Execution > Deployment > Build Tools > Gradle > Gradle JVM** to JDK 17

### Recommended Plugins

- Android Studio bundled plugins (Android, Kotlin, Gradle)
- GitToolBox for enhanced Git integration
- Rainbow Brackets for nested structure visibility

## Troubleshooting Setup

### Build Errors

- **Gradle sync fails**: Delete `.gradle` folder and retry
- **KSP errors**: Ensure `ksp()` is used instead of `annotationProcessor()` or `kapt()`
- **Room compilation errors**: Verify all entity classes have `@Entity` and `@PrimaryKey`

### Device Issues

- **Installation fails**: Ensure USB debugging is enabled and device is authorized
- **App crashes on launch**: Check logcat for missing permissions or Hilt injection errors

## Next Steps

1. Read [ARCHITECTURE.md](ARCHITECTURE.md) for system design
2. Read [TESTING.md](TESTING.md) for testing guidelines
3. Read [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow
