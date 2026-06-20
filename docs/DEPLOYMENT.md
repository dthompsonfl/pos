# EnterprisePOS Deployment Guide

## Deployment Environments

| Environment | Purpose | Build Variant |
|-------------|---------|---------------|
| Development | Local testing and feature work | `debug` |
| Staging | Pre-production validation | `staging` |
| Production | Live customer-facing | `release` |

## Build Variants

The project defines three build variants in `app/build.gradle.kts`:

- `debug`: Debug symbols, no minification, test payment keys
- `staging`: Release optimizations, staging API endpoints, test payment keys
- `release`: Full optimization, ProGuard, production API, live payment keys

## CI/CD Pipeline

### GitHub Actions Workflow

```yaml
name: Build and Deploy
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: ./gradlew test
      - run: ./gradlew connectedCheck
  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: ./gradlew :app:assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: release-apk
          path: app/build/outputs/apk/release/*.apk
```

## Release Checklist

Before every production release:

1. **Version Bump**: Update `versionName` and `versionCode` in `app/build.gradle.kts`
2. **Changelog**: Add release notes to `CHANGELOG.md`
3. **Tests**: All unit and instrumented tests pass (`./gradlew test connectedCheck`)
4. **Security**: No secrets in source code; run `./gradlew checkSecrets`
5. **Database**: Verify migrations are registered and tested
6. **ProGuard**: Check mapping file is generated for crash symbolication
7. **Signing**: Release APK is signed with production keystore
8. **Tagging**: Create Git tag `vX.Y.Z` after merge to main

## Distribution

### Google Play Store

1. Build signed release AAB: `./gradlew :app:bundleRelease`
2. Upload `app/build/outputs/bundle/release/*.aab` to Google Play Console
3. Roll out to internal testing track first
4. Promote to production after validation period

### Enterprise Distribution

For corporate-managed devices using MDM:

1. Build signed release APK: `./gradlew :app:assembleRelease`
2. Distribute APK via MDM portal (VMware Workspace ONE, Microsoft Intune, etc.)
3. Configure forced update policy for critical security releases

### Sideloading

For on-premise or kiosk deployments:

1. Enable **Unknown Sources** in device settings
2. Install via ADB: `adb install app-release.apk`
3. Or use internal app store / file manager

## Rollback Procedure

If a release introduces critical issues:

1. Immediately halt promotion in Google Play Console
2. Build and deploy previous version with incremented `versionCode`
3. Communicate to store managers via admin dashboard
4. Investigate and prepare hotfix release

## Monitoring

After deployment:

- Monitor crash rates via Firebase Crashlytics
- Track payment success rates via Stripe Dashboard
- Monitor sync queue depth via admin dashboard
- Review audit logs for anomalies

## Database Migrations

When releasing schema changes:

1. Increment database version in `PosDatabase`
2. Add `Migration` object in `PosMigrations`
3. Write instrumented migration test in `PosMigrationsTest`
4. Verify rollback is not possible (Room does not support downgrades)

## Environment Configuration

| Config | Development | Staging | Production |
|--------|-------------|---------|------------|
| API Base | `https://api.dev.enterprisepos.com` | `https://api.staging.enterprisepos.com` | `https://api.enterprisepos.com` |
| Stripe | Test keys | Test keys | Live keys |
| Sync Interval | 30 seconds | 60 seconds | 120 seconds |
| Log Level | Verbose | Debug | Error |
| Analytics | Disabled | Enabled | Enabled |
