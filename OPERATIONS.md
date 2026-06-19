# Operations

## Opening a shift

Before accepting payments, an employee must open a shift:

1. **Login** with PIN at the login screen
2. Tap **Shift** → **Open Shift**
3. Count the cash drawer and enter the starting amount
4. Tap **Open Shift** — the drawer is audited as `SHIFT_OPENED` with the starting cash

If a shift is already open on this register, it's auto-closed (with `SHIFT_CLOSED` audit) before the new one opens.

## Taking an order

### Restaurant (dine-in)
1. Tap **Tables** → select an available table
2. Choose **Dine-In** → enter guest count → **Start Order**
3. Tap **Menu** → tap items to add to the cart
4. Modify quantities with +/− buttons
5. Add notes per line (e.g., "no onions")
6. Tap **Kitchen** to send the order to the kitchen display
7. When the customer is ready to pay, tap **Charge**
8. Select payment method (Stripe / Square / Shopify / Cash)
9. For cash: enter amount tendered → confirm → drawer opens → change due displayed
10. For card: insert/tap/swipe on the reader → approval displayed
11. Receipt prints (or queues if printer unavailable)
12. Order is marked PAID; table returns to AVAILABLE after a cleaning delay

### To-Go / Takeout
1. Tap **Tables** → **To-Go**
2. Add items, take payment, print receipt
3. No table assignment needed

### Retail
1. Tap **Tables** → **Retail**
2. Add items by tapping or scanning barcodes
3. Take payment, print receipt

## Applying discounts

1. In the cart, tap **Discount**
2. Choose a percentage (5%, 10%, 15%, 20%, 25%, 50%, 100%)
3. Discounts >25% require manager override:
   - A manager PIN prompt appears
   - Manager enters PIN → override granted → discount applied
   - Audit log records `MANAGER_OVERRIDE_GRANTED` + `DISCOUNT_APPLIED`
4. The discount applies to the order subtotal (not per-line)

For line-level discounts, long-press a line item and select "Discount this item".

## Refunds

1. Find the original order in **Reports** → **Recent Orders**
2. Tap the order → **Refund**
3. Select items to return and quantities
4. Choose reason (defective, customer changed mind, etc.)
5. If restocking: check "Return to inventory"
6. Confirm → refund processed via the original payment provider
7. Refund receipt prints
8. Audit log records `PAYMENT_REFUNDED` with reason

Cash refunds require `canProcessRefunds` permission and open the drawer with `AuditAction.DRAWER_OPENED` reason "Cash refund".

## Voids

Voids cancel an open (unpaid) order. Paid orders must be refunded, not voided.

1. Find the open order
2. Tap **Void**
3. Enter a reason (required)
4. If the order total exceeds the void threshold (configurable in Settings), manager override is required
5. Order is marked VOIDED with `closedAt` timestamp
6. Audit log records `ORDER_VOIDED` with reason

Voids do NOT affect historical sales totals — they only cancel future state.

## Tips

1. After charging, the tip screen appears (for dining modes)
2. Select 15%, 18%, 20%, 25%, or **No Tip**, or **Custom**
3. For card payments: the tip is added to the capture amount before the final capture
4. Tips are persisted in `TipLogEntity` with `orderId`, `employeeId`, `amount`, `tipType`
5. At shift close, tips are summed by employee for the tip pool

Tip pooling modes (configured in Settings):
- **NONE** — each server keeps their own tips
- **EVEN_SPLIT** — total tips divided equally among all employees on shift
- **HOURS_WEIGHTED** — tips divided proportionally to hours worked

## Z-Report

At end-of-day, the manager closes the shift:

1. Tap **Shift** → **Close Shift & Print Z-Report**
2. Count the cash drawer and enter the actual amount
3. The system computes:
   - Expected cash = starting cash + cash payments − cash refunds − paid-outs
   - Over/short = counted cash − expected cash
4. Tap **Close Shift**
5. The Z-Report is generated and persisted permanently:
   - Gross sales
   - Returns / Refunds
   - Discounts
   - Net sales
   - Tax collected (per tax code)
   - Tips
   - Cash total
   - Card total (per provider)
   - Other tenders (gift cards, etc.)
   - Paid-in / Paid-out
   - No-sale count
   - Over/short
   - Transaction count, refund count, void count
   - Employee sales breakdown
6. The Z-Report prints to the receipt printer
7. Audit log records `SHIFT_CLOSED` with the over/short amount in the reason field

## End of day checklist

1. Close all open tables (verify no orders are still open)
2. Reconcile any pending sync conflicts (Dashboard → Conflicts)
3. Count cash drawer
4. Close shift → generate Z-Report
5. Verify Z-Report over/short is within tolerance (typically ±$5)
6. Investigate any large over/short before next shift
7. Logout

## Diagnostics

If something is wrong, open **Settings → Diagnostics** to see:
- App version
- Device ID
- Register ID
- Backend connectivity status
- Sync queue depth
- Last successful sync time
- Last failed sync error
- Printer connection status
- Reader connection status
- Last successful payment timestamp
- Last failed payment error

Tap **Export Support Bundle** to generate a ZIP with:
- App version + device info
- Last 100 audit log entries
- Sync queue state
- Printer/reader status
- (Secrets are REDACTED)
