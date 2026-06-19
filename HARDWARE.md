# Hardware

## Supported hardware

### Receipt printers (ESC/POS-compatible)

| Connection | Driver | Status |
|------------|--------|--------|
| USB | `UsbPrinterDriver` | Interface ready; requires USB Host permission flow |
| Bluetooth (SPP) | `BluetoothPrinterDriver` | Interface ready; requires BT permissions |
| Network (TCP 9100) | `NetworkPrinterDriver` | Interface ready; requires network access |

Tested printer models (in production deployments):
- Star TSP143 (USB + Bluetooth + Network)
- Epson TM-m30 (Bluetooth)
- Star TSP100IIIW (Wi-Fi)
- Bixolon SRP-350III (USB)

**Driver contract:** Every `PrinterDriver` returns `Result<Unit>` from `print()`. Real I/O is performed; failures are NOT silently swallowed. If a print fails, the receipt is queued in `ReceiptQueue` and retried.

### Cash drawers

| Driver | Trigger |
|--------|---------|
| `PrinterKickCashDrawerDriver` | Sends ESC p m t1 t2 to a connected printer (drawer kicks on pin 2) |
| `UsbRelayCashDrawerDriver` | Direct USB relay pulse for dedicated drawer setups |

Drawer opens are ALWAYS audited:
- `AuditAction.DRAWER_OPENED` for normal opens (after cash tender)
- `AuditAction.NO_SALE` for drawer-only opens (requires manager permission)
- `AuditAction.PAID_IN` / `PAID_OUT` for cash movements

### Barcode scanners

| Type | Driver | Notes |
|------|--------|-------|
| USB HID (keyboard wedge) | `KeyboardWedgeScanner` | Forwards Activity key events; accumulates until ENTER |
| Camera (ML Kit) | `CameraScanner` | Fallback for tablets without hardware scanner |

The host `Activity` must forward `KeyEvent` to `KeyboardWedgeScanner.onKeyEvent()`. The scanner emits a complete barcode via `Flow<String>` on the ENTER key.

### Customer-facing displays

| Type | Driver | Notes |
|------|--------|-------|
| HDMI / USB-C external | `PresentationSecondaryDisplay` | Uses Android `Presentation` API on secondary `Display` |
| None | `NoopSecondaryDisplay` | Default in debug builds |

The display shows:
- Welcome screen (when no active order)
- Live cart (items + running total)
- Payment prompt ("Insert, tap, or swipe")
- Thank-you screen (post-payment)

## Receipt rendering

`ReceiptRenderer` produces ESC/POS byte streams:

1. **Initialize** (ESC @)
2. **Header** (store name centered, address, phone)
3. **Order metadata** (order ID, date, server, table, dining mode, guest count)
4. **Line items** (qty × name, unit price, line total, notes, modifiers)
5. **Totals** (subtotal, line discounts, order discount, tax by code, tip, grand total)
6. **Payment summary** (provider, last4, amount)
7. **Footer** (thank-you message)
8. **Drawer kick** (ESC p m t1 t2)
9. **Paper cut** (GS V 1)

For kitchen tickets, a separate `renderKitchenTicket()` produces:
- Order ID + table + dining mode (large font)
- Items grouped by station
- Notes prominently displayed
- No prices
- Paper cut

## Kitchen routing

Each `Product` has an optional `kitchenRoutingKey` (e.g. `"grill"`, `"fryer"`, `"salad"`, `"pizza"`, `"bar"`). When an order is sent to kitchen:

1. `OrderRepository.sendToKitchen()` marks each line `sentToKitchen = true`
2. `PrinterManager.printKitchenTicket()` is called per station
3. For each station with a configured printer, a station-specific ticket is printed containing only that station's items
4. If no printer is configured for a station, the ticket is queued

To configure station-to-printer mapping, use the Settings → Printer/KDS Routing screen.

## Receipt queue

If a print fails (printer disconnected, out of paper, etc.):
1. The receipt is added to `ReceiptQueue` (Room table)
2. A retry is scheduled when the printer reconnects
3. The user sees "Receipt queued — will print when printer is available"
4. A manager can reprint any queued receipt from Reports → Receipt Archive
5. Every reprint writes `AuditAction.RECEIPT_PRINTED` to the audit log

## Failure handling

Hardware operations NEVER claim success without real completion:

```kotlin
// BAD (v1 behavior):
override suspend fun print(data: ByteArray): Result<Unit> = Result.success(Unit) // LIES

// GOOD (v2 behavior):
override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
    require(isConnected) { "Printer not connected" }
    val written = connection.bulkTransfer(endpoint, data, data.size, TIMEOUT_MS)
    require(written == data.size) { "Partial write: $written / ${data.size}" }
}
```

If a driver cannot perform real I/O, it returns `Result.Failure` with a descriptive error.

## Permissions

```xml
<!-- USB printer -->
<uses-feature android:name="android.hardware.usb.host" android:required="false" />

<!-- Bluetooth printer -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Camera scanner -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

Runtime permissions must be requested before connecting:
- `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (API 31+)
- `CAMERA` (for camera scanner)

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| "Printer not connected" | Driver not initialized | Call `PrinterDriver.connect()` before `print()` |
| Partial print | Buffer overflow | Split large receipts into chunks of 4KB |
| Cash drawer won't open | Printer not connected | Drawer kick requires a connected printer |
| Scanner not emitting | Activity not forwarding keys | Override `Activity.dispatchKeyEvent` to call `KeyboardWedgeScanner.onKeyEvent` |
| Customer display blank | Display not attached | Check `DisplayManager.getDisplays()` length > 1 |
