# EnterprisePOS Hardware Integration Guide

## Supported Devices

EnterprisePOS abstracts hardware peripherals through a common interface layer. Both physical and simulated implementations are supported.

## Receipt Printer

### ESC/POS Thermal Printer

**Supported Models**: Epson TM-T88V, Star TSP100, Bixolon SRP-330

**Connection**: USB, Bluetooth, or Network (TCP/IP)

**Capabilities**:
- Standard receipt printing (80mm width)
- Kitchen chit printing (order routing to kitchen)
- X and Z report generation
- QR code and barcode printing
- Cash drawer kick
- Full and partial cut

**Configuration**:
```json
{
  "printerType": "ESC_POS",
  "connectionType": "USB",
  "address": "",
  "paperWidth": 80,
  "characterSet": "UTF-8"
}
```

### Simulated Printer

For testing and development without physical hardware:

```kotlin
val printer = SimulatedReceiptPrinterManager(logger)
```

Simulated printer logs all print commands to console for verification.

## Cash Drawer

### Physical Drawer

**Connection**: RJ12 interface connected to receipt printer

**Operations**:
- Open via printer kick command
- Status detection (open/closed) via GPIO or API

### Simulated Drawer

```kotlin
val drawer = SimulatedCashDrawerManager(logger)
```

Provides in-memory state tracking for testing.

## Barcode Scanner

### Supported Scanners

- USB HID scanners (generic keyboard emulation)
- Bluetooth scanners (Socket Mobile, Zebra)
- Camera-based scanning (ZXing)

### Formats

- EAN-13
- UPC-A
- Code 128
- QR Code
- Data Matrix

### Simulated Scanner

```kotlin
val scanner = SimulatedBarcodeScannerManager()
scanner.emitScan("1234567890123", BarcodeFormat.EAN_13)
```

## Customer Display

### Pole Display

**Supported**: Epson DM-D30, Bixolon BCD-1100

**Connection**: USB Serial or COM port

**Messages**:
- Welcome message
- Running total with tax breakdown
- Item name and price
- Thank you message

### Simulated Display

```kotlin
val display = SimulatedCustomerDisplayManager(logger)
display.showRunningTotal(subtotal = "$10.00", tax = "$0.82", total = "$10.82")
```

## Payment Terminal

### Stripe Terminal

**Supported Readers**: Verifone P400, BBPOS Chipper 2X, WisePad 3

**Connection**: Bluetooth or USB

**Capabilities**:
- Card present (tap, insert, swipe)
- PIN entry
- Receipt selection (print, email, text, no receipt)
- Offline processing with automatic retry

## Hardware Discovery

```kotlin
val manager = HardwareManagerFactory.create(context, config)
val printers = manager.discoverPrinters()
val readers = manager.discoverCardReaders()
val scanners = manager.discoverBarcodeScanners()
```

## Error Handling

All hardware operations return `Result<T, HardwareError>`:

| Error | Cause | Recovery |
|-------|-------|----------|
| CONNECTION_LOST | Cable disconnected, Bluetooth out of range | Reconnect peripheral |
| DEVICE_BUSY | Another operation in progress | Wait and retry |
| PAPER_OUT | Thermal paper depleted | Load paper and retry |
| DRAWER_OPEN | Cash drawer is open | Close drawer and retry |
| TIMEOUT | No response from device | Check connection and retry |
| UNSUPPORTED | Feature not available on device | Use alternative method |

## Testing Without Hardware

All hardware modules include simulated implementations that are used in unit tests and can be enabled in debug builds:

```gradle
debug {
    buildConfigField "boolean", "USE_SIMULATED_HARDWARE", "true"
}
```

## Calibration

### Printer

1. Print test page from settings
2. Verify alignment and cut position
3. Adjust offset in hardware settings if needed

### Scanner

1. Scan calibration barcode
2. Verify scan events appear in debug log
3. Check format detection accuracy

### Display

1. Send welcome message
2. Verify character visibility and brightness
3. Adjust contrast if supported

## Troubleshooting

| Symptom | Cause | Solution |
|---------|-------|----------|
| Printer not found | Wrong connection type | Verify USB/Bluetooth/Network setting |
| Garbled text | Wrong character encoding | Set UTF-8 in printer settings |
| Scanner not responding | HID mode disabled | Enable HID keyboard emulation |
| Drawer won't open | Printer not connected | Drawer requires printer connection |
| Display blank | Wrong COM port | Verify port in device manager |

## Integration Checklist

Before deploying to production:

- [ ] All peripherals discovered and connected
- [ ] Test print successful
- [ ] Scanner reads all expected barcode formats
- [ ] Drawer opens and status detected
- [ ] Display shows all message types
- [ ] Payment terminal processes test transaction
- [ ] Error states handled gracefully
- [ ] Fallback to simulated mode documented
