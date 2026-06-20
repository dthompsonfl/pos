# Stripe Terminal Integration

This POS ships with a fully-scaffolded Stripe Terminal adapter in `payment-stripe/`. The SDK
calls are stubbed with simulated flow so the project builds without Stripe credentials. To go
live, replace the simulate blocks with the real Stripe Terminal calls as documented below.

## 1. Get Stripe credentials

1. Create a Stripe account at https://dashboard.stripe.com/register
2. Enable Terminal in the Stripe Dashboard (Settings → Business settings → Stripe Terminal)
3. Create a **Location** for your store (Terminal → Locations → Add)
4. Copy the Location ID (`loc_...`)
5. Implement a **Connection Token endpoint** on your backend that returns a connection token
   from `stripe.terminal.ConnectionToken.create()`. See
   https://docs.stripe.com/terminal/sdk/ios#connection-token

## 2. Add SDK

`payment-stripe/build.gradle.kts` already includes:

```kotlin
api(libs.stripe_terminal)  // com.stripe:stripeterminal:3.7.1
```

## 3. Initialize

In `StripePaymentProvider.initialize()`:

```kotlin
override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.catching {
    this.config = config
    val tokenProvider = ConnectionTokenProvider {
        // Fetch from your backend:
        withContext(Dispatchers.IO) {
            val resp = httpClient.get("https://your-backend.example.com/stripe/connection-token")
            ConnectionToken(JSON.parse(resp.body<String>())["secret"])
        }
    }
    Terminal.init(
        context,  // pass via constructor
        TerminalConfiguration(
           LogLevel.VERBOSE,
            config.environment.toStripe()
        ),
        tokenProvider,
        terminalListener
    )
}
```

## 4. Discover & connect readers

```kotlin
override suspend fun discoverReaders(): Result<List<DiscoveredReader>> {
    val config = DiscoveryConfiguration(
        DiscoveryMethod.BLUETOOTH_SCAN,
        isSimulated = config.environment == ProviderEnvironment.SANDBOX
    )
    return suspendCancellableCoroutine { cont ->
        Terminal.discoverReaders(config, object : DiscoveryListener {
            override fun onUpdateReaders(readers: List<com.stripe.stripeterminal.model.Reader>) {
                // Map to our DiscoveredReader and emit
            }
            override fun onDiscoveryComplete(readers: List<Reader>) { /* final */ }
            override fun onError(e: TerminalException) { cont.resumeWith(Result.failure(e)) }
        }, null /* cancelable */)
    }
}

override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> = Result.catching {
    val cfg = ConnectionConfiguration.BluetoothConnectionConfiguration(reader.id)
    val connected = Terminal.connectReader(cfg)
    // store connected for later use
}
```

## 5. Create Payment Intent

```kotlin
override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> {
    val params = PaymentIntentParameters.Builder(request.amount.minorUnits.toInt())
        .setCurrency(request.currency.lowercase())
        .setDescription(request.description)
        .setCaptureMethod(CaptureMethod.MANUAL)
        .setMetadata(request.metadata)
        .build()
    val intent = Terminal.createPaymentIntent(params)
    return Result.success(PaymentIntentHandle(
        provider = id, intentId = intent.id, secret = intent.secret,
        amount = request.amount, currency = request.currency, createdAt = System.currentTimeMillis()
    ))
}
```

## 6. Collect & Confirm Payment

```kotlin
override suspend fun collectPayment(
    handle: PaymentIntentHandle,
    events: ((PaymentEvent) -> Unit)?
): Result<PaymentResult> = suspendCancellableCoroutine { cont ->
    val intent = Terminal.retrievePaymentIntent(handle.secret)
    events?.invoke(PaymentEvent.InsertCard())

    Terminal.collectPaymentMethod(intent, object : PaymentIntentCallback {
        override fun onSuccess(intent: PaymentIntent) {
            events?.invoke(PaymentEvent.Processing())
            Terminal.confirmPaymentIntent(intent, object : PaymentIntentCallback {
                override fun onSuccess(intent: PaymentIntent) {
                    val charge = intent.charges.first()
                    events?.invoke(PaymentEvent.Success())
                    cont.resume(Result.success(PaymentResult(
                        id = randomPaymentId(), provider = id,
                        providerTransactionId = intent.id,
                        amount = Money.ofMinor(intent.amount.toLong()),
                        currency = intent.currency.uppercase(),
                        cardBrand = charge.paymentMethod.card?.brand,
                        last4 = charge.paymentMethod.card?.last4,
                        entryMode = charge.paymentMethod.card?.presentmentStyle,
                        receiptUrl = charge.receiptUrl,
                        capturedAt = System.currentTimeMillis()
                    )))
                }
                override fun onFailure(e: TerminalException) {
                    val code = when (e.errorCode) {
                        TerminalException.PaymentIntentError.CARD_DECLINED -> PaymentErrorCode.DECLINED
                        TerminalException.PaymentIntentError.CARD_READ_FAILED -> PaymentErrorCode.CARD_READ_FAILED
                        else -> PaymentErrorCode.UNKNOWN
                    }
                    events?.invoke(PaymentEvent.Declined(e.errorMessage))
                    cont.resume(Result.failure(AppError.Payment(code, e.errorMessage, e.errorCode)))
                }
            }, null)
        }
        override fun onFailure(e: TerminalException) {
            events?.invoke(PaymentEvent.Error(e.errorMessage))
            cont.resume(Result.failure(AppError.Payment(PaymentErrorCode.CARD_READ_FAILED, e.errorMessage)))
        }
    }, null)
}
```

## 7. Refunds

```kotlin
override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> {
    val chargeId = ... // lookup from your local PaymentEntity
    val params = RefundParameters.Builder(chargeId)
        .setAmount(amount?.minorUnits?.toInt())
        .build()
    val refund = Terminal.refundPayment(params, callback)
    return Result.success(RefundResult(
        id = refund.id, originalPaymentId = paymentId,
        amount = Money.ofMinor(refund.amount.toLong()),
        status = if (refund.status == RefundStatus.SUCCEEDED) RefundStatus.SUCCEEDED else RefundStatus.PENDING,
        providerRefundId = refund.id, createdAt = System.currentTimeMillis()
    ))
}
```

## 8. Offline Mode

Stripe Terminal supports offline payment queueing for up to 9 days. To enable:

```kotlin
val offlineConfig = OfflineConfiguration(
    enabled = true,
    onPaymentIntentForwarded = { intent -> /* enqueue for upload */ }
)
TerminalConfiguration(offlineConfiguration = offlineConfig)
```

Our `PaymentRouter` already supports `PaymentCapability.OFFLINE_MODE` — when the routing
policy allows it, the request sets `enableOffline = true` and the provider may queue the
payment for later upload.

## 9. Permissions

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## 10. PCI Compliance

Card-present payments are PCI-DSS scope. Stripe Terminal is a validated SPoC solution,
which keeps your scope low — but you must:

- Never log card numbers
- Use Stripe's `cardPresent` payment methods only via the SDK
- Keep your backend connection token endpoint authenticated
- Re-validate annually via Stripe's SAQ
