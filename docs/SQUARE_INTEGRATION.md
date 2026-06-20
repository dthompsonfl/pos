# Square Reader SDK Integration

This POS includes a fully-scaffolded Square Reader SDK adapter in `payment-square/`. The
SDK calls are stubbed — replace them with real Square calls as documented below.

## 1. Get Square credentials

1. Create a Square Developer account at https://developer.squareup.com
2. Create a new Application
3. Note the **Application ID** and **Reader SDK Repository credentials** (under Reader SDK tab)
4. Add Square SDK maven repo in `settings.gradle.kts`:
   ```kotlin
   maven { url = uri("https://sdk.squareup.com/public/android") }
   ```
5. Replace the placeholder in `payment-square/build.gradle.kts`:
   ```kotlin
   api("com.squareup.sdk.reader:reader-sdk-hardware:1.7.5")
   ```

## 2. Initialize

```kotlin
override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.catching {
    this.config = config
    ReaderSdk.initialize(context)
}
```

## 3. Pair a Reader

Square Reader SDK doesn't expose a discrete reader list — instead, you launch
`ReaderSettingsActivity` and let the user pair via Square's UI:

```kotlin
override suspend fun discoverReaders(): Result<List<DiscoveredReader>> {
    // Square doesn't expose a list — return a single sentinel that triggers ReaderSettings flow.
    return Result.success(listOf(
        DiscoveredReader(
            id = "square-pair",
            displayName = "Pair a Square Reader",
            model = "pair-flow",
            connectionType = ConnectionType.BLUETOOTH
        )
    ))
}

override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> {
    val intent = Intent(context, ReaderSettingsActivity::class.java)
    // launch via ActivityResultLauncher and await callback
    return Result.success(Unit)
}
```

## 4. Take a Payment

```kotlin
override suspend fun collectPayment(
    handle: PaymentIntentHandle,
    events: ((PaymentEvent) -> Unit)?
): Result<PaymentResult> {
    val checkout = CheckoutParameters.Builder(
        Money.ofMinor(handle.amount.minorUnits.toInt()),
        CurrencyCode.valueOf(handle.currency)
    ).build()

    events?.invoke(PaymentEvent.InsertCard())
    val result = CardEntry.startCardEntryActivity(context, checkout)
    // The result arrives via onActivityResult in the host Activity.
    // You'll need to wire a callback channel from the Activity back to here.
    ...
}
```

See Square's [mobile-payment-intent](https://developer.squareup.com/docs/payments/mobile-sdk-android)
docs for the latest callback pattern.

## 5. Refunds

```kotlin
override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> {
    val refundParams = RefundParameters.Builder()
        .paymentId(paymentId.value)
        .amount(Money.ofMinor(amount.minorUnits.toInt()))
        .build()
    val result = RefundManager.beginRefund(refundParams, callback)
    ...
}
```

## 6. Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Square's SDK also requires `Bluetooth` and `Location` runtime permissions to be granted
before pairing.
