# EnterprisePOS Troubleshooting Guide

## Common Issues

### Installation

#### App Won't Install

**Symptoms**: `INSTALL_FAILED` error during ADB install or Play Store download fails.

**Causes & Solutions**:
- **Insufficient storage**: Free at least 200MB on device
- **Incompatible Android version**: Verify device runs Android 8.0 (API 26) or higher
- **Conflicting signature**: Uninstall existing app first if signing certificate changed
- **Unknown sources disabled**: Enable `Settings > Security > Unknown Sources` for sideloading

#### App Crashes on Launch

**Symptoms**: White screen then immediate crash; app closes without error message.

**Causes & Solutions**:
- **Missing permissions**: Grant all requested permissions (Storage, Camera, Location)
- **Corrupted database**: Clear app data or reinstall
- **Hilt injection failure**: Check logcat for missing binding errors; verify all modules are included in build
- **Out of memory**: Close background apps; ensure device has 2GB+ RAM free

### Authentication

#### Cannot Log In

**Symptoms**: "Invalid PIN" error or login button unresponsive.

**Causes & Solutions**:
- **Wrong PIN**: Verify correct PIN with manager; PINs are case-sensitive only if alphanumeric
- **Account locked**: Wait for lockout timer or contact manager for override
- **Network unavailable**: Offline mode requires cached credentials; check network for first login
- **Employee deactivated**: Contact admin to reactivate account in dashboard

#### Session Expired Too Quickly

**Symptoms**: Frequent "Session expired" messages during active use.

**Causes & Solutions**:
- **Aggressive timeout**: Admin can adjust session timeout in settings (default 30 minutes)
- **Device time wrong**: Sync device time automatically; certificate validation fails with wrong time
- **Background killing**: Add app to battery optimization whitelist in device settings

### Orders

#### Order Won't Save

**Symptoms**: "Failed to create order" error or infinite spinner.

**Causes & Solutions**:
- **Offline without queue space**: Check sync queue depth; clear completed entries if over 100
- **Database locked**: Kill and restart app; rare SQLite concurrency issue
- **Invalid item**: Verify product is available and not deleted from catalog
- **Missing required field**: Check if dining mode or table assignment is required

#### Payment Failed

**Symptoms**: "Payment declined" or "Cannot process payment" error.

**Causes & Solutions**:
- **Reader not connected**: Verify card reader is paired and connected in settings
- **Offline without cached key**: Some payments require connectivity for first use
- **Card declined**: Ask customer to use different card or payment method
- **Amount mismatch**: Verify payment amount matches order total exactly
- **Split tender error**: Ensure remaining amount is calculated correctly; rounding issues may cause 1-cent differences

#### Cannot Void Order

**Symptoms**: "Void not allowed" error on paid orders.

**Causes & Solutions**:
- **Already paid**: Refund payment first, then void
- **Insufficient permissions**: Manager override required for voids after payment
- **Settlement window closed**: Some card payments cannot be voided after batch settlement; process refund instead
- **Synced order**: Voided orders sync to server; ensure connectivity for audit trail

### Catalog

#### Products Not Showing

**Symptoms**: Empty menu or missing items.

**Causes & Solutions**:
- **Sync pending**: Wait for catalog sync to complete; check sync status in settings
- **Category filter**: Verify correct category is selected
- **Product unavailable**: Check if product was marked unavailable in admin
- **Cache stale**: Pull down to refresh or restart app

#### Price Not Updating

**Symptoms**: Old price displayed after admin change.

**Causes & Solutions**:
- **Sync delay**: Price changes sync within 60 seconds; manual sync from settings
- **Local override**: Check if store-level price override exists
- **Modifier not included**: Verify modifier price is separate from base price

### Hardware

#### Printer Not Found

**Symptoms**: "No printer discovered" in settings.

**Causes & Solutions**:
- **Wrong connection type**: Verify USB/Bluetooth/Network setting matches physical connection
- **Cable issue**: Try different USB cable or Bluetooth pairing
- **Power off**: Ensure printer is powered on and in ready state
- **Driver missing**: Install manufacturer driver if required for USB on some devices
- **IP wrong**: For network printers, verify IP address and subnet match

#### Print Quality Issues

**Symptoms**: Faded, smudged, or misaligned printing.

**Causes & Solutions**:
- **Low paper**: Replace thermal paper roll
- **Dirty head**: Clean thermal print head with alcohol wipe
- **Wrong paper width**: Configure 58mm or 80mm in settings to match installed paper
- **Encoding issue**: Set UTF-8 in printer settings for special characters
- **Cut position wrong**: Adjust cut offset in hardware settings

#### Scanner Not Reading

**Symptoms**: Barcodes scan but no product found, or scanner light doesn't activate.

**Causes & Solutions**:
- **Wrong format**: Verify scanner supports the barcode format (EAN-13, UPC-A, Code 128)
- **HID mode disabled**: Enable keyboard emulation in scanner settings
- **Bluetooth disconnected**: Re-pair scanner in device Bluetooth settings
- **Damaged barcode**: Try manual entry or different barcode
- **Product not in catalog**: Add product with matching barcode in admin

#### Cash Drawer Won't Open

**Symptoms**: Drawer doesn't open on print or manual open command.

**Causes & Solutions**:
- **Printer disconnected**: Drawer connects through printer; verify printer connection first
- **Wrong kick code**: Configure drawer kick command (ESC/POS: `0x10, 0x14, 0x01, 0x00, 0x05`)
- **Mechanical jam**: Check drawer track for obstructions
- **Lock engaged**: Verify drawer key lock is in unlocked position

#### Customer Display Blank

**Symptoms**: Display shows nothing or garbled characters.

**Causes & Solutions**:
- **Wrong COM port**: Verify port in device manager matches configuration
- **Baud rate mismatch**: Set 9600 baud in settings for most pole displays
- **Contrast too low**: Adjust contrast dial on display if available
- **Power issue**: Check USB power or external adapter
- **Wrong protocol**: Some displays use custom protocols; check manufacturer docs

### Sync

#### Sync Stuck

**Symptoms**: "Syncing..." message persists indefinitely.

**Causes & Solutions**:
- **No connectivity**: Check Wi-Fi or cellular data; test with browser
- **Server maintenance**: Check status page or contact support
- **Large queue**: Queue over 100 items may take several minutes; monitor progress in settings
- **Auth expired**: Log out and back in to refresh token
- **Database corruption**: Clear app data and re-login (downloads fresh data)

#### Duplicate Orders

**Symptoms**: Same order appears multiple times in server dashboard.

**Causes & Solutions**:
- **Retry without idempotency**: Ensure `SyncOutboxDao` uses `idempotentId` for retries
- **Network timeout**: Client timed out but server processed request; implement client-side deduplication
- **Clock skew**: Device time differs from server by >5 minutes; sync device time
- **Multiple devices**: Two registers creating same order; verify unique order IDs

### Payment

#### Card Reader Disconnected

**Symptoms**: "Reader not connected" during checkout.

**Causes & Solutions**:
- **Bluetooth off**: Enable Bluetooth in device settings
- **Reader out of range**: Move reader within 10 meters of device
- **Reader paired to another device**: Unpair from other device first
- **Reader low battery**: Charge reader for at least 30 minutes
- **Firmware outdated**: Update reader firmware via Stripe dashboard

#### Offline Payment Declined Later

**Symptoms**: Payment accepted offline but declined during sync.

**Causes & Solutions**:
- **Expired card**: Card expired during offline period; request alternative payment
- **Insufficient funds**: Authorization hold expired; retry with new authorization
- **Network issue during sync**: Retry sync manually from settings
- **Fraud detection**: Stripe flagged transaction; contact support with transaction ID

#### Refund Failed

**Symptoms**: "Refund could not be processed" error.

**Causes & Solutions**:
- **Beyond refund window**: Most card payments allow 120-day refund; use store credit for older transactions
- **Partial settlement**: Refund amount exceeds available balance; wait for settlement
- **Reader not connected**: Connect reader for card-present refund; or use dashboard for card-not-present
- **Permission denied**: Manager role required for refunds; verify employee permissions

### Performance

#### App is Slow

**Symptoms**: Laggy UI, slow screen transitions, delayed button response.

**Causes & Solutions**:
- **Large catalog**: Catalog over 10,000 items may slow search; use category filtering
- **Low memory**: Close background apps; ensure 2GB+ free RAM
- **Database bloat**: Old orders accumulate; archive orders older than 90 days
- **Sync frequency**: Reduce sync interval from 30 to 120 seconds in settings
- **Debug build**: Debug builds are slower; use release build for production

#### Startup is Slow

**Symptoms**: App takes >10 seconds to reach main screen.

**Causes & Solutions**:
- **First launch**: Initial sync downloads full catalog; subsequent launches are faster
- **Database migration**: Major updates may run migrations; wait for completion
- **Corrupted cache**: Clear app cache in device settings
- **Large order history**: Archive old orders to reduce database size

### Database

#### Database Error on Launch

**Symptoms**: "Database error" or "Cannot open database" crash.

**Causes & Solutions**:
- **Corruption from crash**: Clear app data to recreate database (data re-syncs from server)
- **Migration failure**: Check logcat for migration error; verify app is updated to latest version
- **Storage full**: Free device storage; database needs 50MB minimum
- **SQLCipher issue**: If using encrypted database, verify key is correct after reinstall

#### Data Loss

**Symptoms**: Orders, products, or employees missing after restart.

**Causes & Solutions**:
- **Fresh install**: New install starts empty; log in to sync data
- **Database cleared**: App data cleared by user or device manager; re-login to sync
- **Sync conflict**: Server data won over local; check server dashboard for correct data
- **Multi-device**: Changes on one device may not reflect on another; ensure sync completed

## Diagnostic Tools

### Logcat Filtering

Filter relevant logs:

```bash
adb logcat -s EnterprisePOS:V *:S
```

### Database Inspection

Pull and inspect database:

```bash
adb shell run-as com.enterprise.pos cp databases/pos.db /sdcard/pos.db
adb pull /sdcard/pos.db
sqlite3 pos.db ".tables"
```

### Network Inspection

Use Charles Proxy or Android Studio Network Profiler to inspect API calls.

### Hardware Diagnostics

Built-in diagnostic screen accessible from `Settings > Hardware > Diagnostics`:
- Printer test page
- Scanner test mode
- Drawer open/close test
- Display message test
- Reader connection test

## Contact Support

If issue persists after troubleshooting:

1. **Collect logs**: Export logcat from `Settings > About > Export Logs`
2. **Note device info**: Model, Android version, app version
3. **Document steps**: Exact steps to reproduce the issue
4. **Contact**: support@enterprisepos.com with above information

**Emergency**: For payment processing down or security incidents, call +1-800-555-0199 (24/7)
