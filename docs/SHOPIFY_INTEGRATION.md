# Shopify POS Integration

Shopify is integrated via two complementary paths:

1. **Deep-link handoff** — checkout launches the Shopify POS app via `shopifypos://` scheme;
   Shopify handles card entry and returns to our app via `enterprise-pos://payment-callback`.
2. **Admin API** — used for order sync, refunds, and catalog mirroring.

## 1. Get Shopify credentials

1. Create a Shopify Partner account at https://partners.shopify.com
2. Create a Development Store (or use an existing one)
3. Create a Custom App in the Shopify Admin with these scopes:
   - `read_orders`, `write_orders`
   - `read_products`, `write_products`
   - `read_customers`, `write_customers`
   - `read_payments`, `write_payments`
4. Copy the **Admin API access token** and the shop domain (`your-store.myshopify.com`)

## 2. Admin API implementation

Implement `ShopifyAdminApi` using Ktor (already a dependency in `payment-shopify/`):

```kotlin
class KtorShopifyAdminApi(
    private val client: HttpClient
) : ShopifyAdminApi {

    override suspend fun verifyToken(shop: String, token: String): Boolean {
        val resp = client.get("https://$shop/admin/api/2024-07/shop.json") {
            header("X-Shopify-Access-Token", token)
        }
        return resp.status.isSuccess()
    }

    override suspend fun refund(shop: String, token: String, orderId: String, amount: Money?): Boolean {
        val resp = client.post("https://$shop/admin/api/2024-07/orders/$orderId/refunds.json") {
            header("X-Shopify-Access-Token", token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonObject("refund") {
                    amount?.let { put("amount", it.asBigDecimal.toString()) }
                    put("notify", false)
                }
            }.toString())
        }
        return resp.status.isSuccess()
    }

    override suspend fun pullOrders(shop: String, token: String): List<String> {
        val resp = client.get("https://$shop/admin/api/2024-07/orders.json?status=any") {
            header("X-Shopify-Access-Token", token)
        }
        val body = resp.body<JsonObject>()
        return body["orders"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
    }

    override suspend fun pushProduct(shop: String, token: String, productJson: String): Boolean {
        val resp = client.post("https://$shop/admin/api/2024-07/products.json") {
            header("X-Shopify-Access-Token", token)
            contentType(ContentType.Application.Json)
            setBody(productJson)
        }
        return resp.status.isSuccess()
    }
}
```

## 3. Deep-link handoff

Shopify POS exposes a `shopifypos://` scheme for cart handoff:

```
shopifypos://cart/{amount}?return_url={callback}&shop={domain}&note={note}
```

Our `ShopifyPaymentProvider.createPaymentIntent()` already constructs this URL. The launcher
callback receives:

```
enterprise-pos://payment-callback?status=success&order_id=...&payment_id=...
```

Register a deep-link handler in `MainActivity` (the manifest already declares the
`enterprise-pos://payment-callback` intent filter):

```kotlin
NavHost(...) {
    composable("payment-callback") { entry ->
        val status = entry.arguments?.getString("status")
        val orderId = entry.arguments?.getString("order_id")
        // Forward to CheckoutViewModel to complete the payment
    }
}
```

## 4. Order sync

When a Shopify payment completes, mirror the order back into our local Room DB so it
appears in reports and supports refunds:

1. Shopify callback returns a Shopify order ID
2. Pull the order via `GET /admin/api/2024-07/orders/{id}.json`
3. Convert to our `Order` domain model via a mapper
4. Upsert into Room + enqueue a `SyncQueueEntity` for upstream reconciliation

## 5. Catalog sync

To use Shopify as the source of truth for the menu:

1. Schedule a periodic `WorkManager` job pulling `GET /admin/api/2024-07/products.json`
2. Map each Shopify Product → our `ProductEntity` + `VariantEntity`
3. Upsert via `CatalogDao`
4. Local edits are pushed back via `pushProduct(shop, token, json)`

## 6. Customer sync

Mirror Shopify customers into our `customers` table the same way. Loyalty points and store
credit are tracked locally; we sync them as Shopify customer metafields for cross-channel
visibility.

## 7. Permissions

Shopify integration doesn't require extra Android permissions — it uses the standard
`INTERNET` permission already declared.

## 8. Why use Shopify?

- If your business already runs on Shopify for online orders, this integration keeps your
  in-person and online channels unified.
- Customers see the same inventory, discounts, and loyalty program regardless of channel.
- Refunds issued in our POS automatically appear in Shopify and vice versa.

## 9. Limitations

- Shopify POS doesn't support card-present via our app directly — the actual card reader
  is Square or Shopify's own hardware, accessed via the Shopify POS app.
- For a true native card-present flow, prefer Stripe Terminal or Square Reader SDK as the
  primary provider, and reserve Shopify for online-order fulfillment.
