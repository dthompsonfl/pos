package com.enterprise.pos.seed

import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.data.db.dao.CatalogDao
import com.enterprise.pos.data.db.dao.CustomerDao
import com.enterprise.pos.data.db.dao.EmployeeDao
import com.enterprise.pos.data.db.dao.GiftCardDao
import com.enterprise.pos.data.db.dao.PromotionDao
import com.enterprise.pos.data.db.dao.ReservationDao
import com.enterprise.pos.data.db.dao.StoreDao
import com.enterprise.pos.data.db.dao.TableDao
import com.enterprise.pos.data.db.entity.CategoryEntity
import com.enterprise.pos.data.db.entity.CustomerEntity
import com.enterprise.pos.data.db.entity.EmployeeEntity
import com.enterprise.pos.data.db.entity.GiftCardEntity
import com.enterprise.pos.data.db.entity.InventoryEntity
import com.enterprise.pos.data.db.entity.ProductEntity
import com.enterprise.pos.data.db.entity.PromotionEntity
import com.enterprise.pos.data.db.entity.RegisterEntity
import com.enterprise.pos.data.db.entity.ReservationEntity
import com.enterprise.pos.data.db.entity.StoreEntity
import com.enterprise.pos.data.db.entity.TableEntity
import com.enterprise.pos.data.db.entity.VariantEntity
import com.enterprise.pos.domain.model.AgeRestriction
import com.enterprise.pos.domain.model.ProductType
import com.enterprise.pos.domain.model.PromotionScope
import com.enterprise.pos.domain.model.PromotionType
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.TaxCategory
import java.util.UUID
import java.util.Calendar

/**
 * Seeds the local DB on first launch with a representative restaurant: store, register, menu,
 * employees, customers, dining tables, gift cards, promotions, and reservations.
 *
 * Idempotent — every row has a stable UUID so re-running won't create duplicates.
 */
class DatabaseSeeder(
    private val storeDao: StoreDao,
    private val catalogDao: CatalogDao,
    private val employeeDao: EmployeeDao,
    private val customerDao: CustomerDao,
    private val tableDao: TableDao,
    private val giftCardDao: GiftCardDao,
    private val promotionDao: PromotionDao,
    private val reservationDao: ReservationDao
) {
    suspend fun seedIfEmpty() {
        if (storeDao.first() != null) return
        seedStoresRegistersEmployees()
        seedCustomers()
        seedMenu()
        seedTables()
        seedGiftCards()
        seedPromotions()
        seedReservations()
    }

    private suspend fun seedStoresRegistersEmployees() {
        val storeId = "store-demo-001"
        storeDao.upsertStore(
            StoreEntity(
                id = storeId,
                name = "The Garden Bistro",
                address = "123 Main St, Chicago, IL 60601",
                phone = "(312) 555-0100",
                taxIdentifier = "IL-12345678",
                currency = "USD",
                timezone = "America/Chicago"
            )
        )
        storeDao.upsertRegisters(
            listOf(
                RegisterEntity(id = "register-001", storeId = storeId, name = "Front Register", deviceIdentifier = "front-01", active = true),
                RegisterEntity(id = "register-002", storeId = storeId, name = "Bar Register", deviceIdentifier = "bar-01", active = true)
            )
        )
        val now = System.currentTimeMillis()
        // PINs are stored as PBKDF2 hashes, never as raw digits.
        // Demo PINs (debug builds only — release builds should NOT seed):
        //   1111 = Manager, 2222 = Sam Server, 3333 = Jordan Server,
        //   4444 = Casey Host, 5555 = Riley Bartender, 6666 = Morgan Cashier
        val h = com.enterprise.pos.domain.security.PinHasher
        employeeDao.upsert(EmployeeEntity(id = "emp-admin", name = "Alex Manager", pinHash = h.hash("1111"), role = "MANAGER", active = true, email = "alex@gardenbistro.com", phone = "312-555-1001", createdAt = now))
        employeeDao.upsert(EmployeeEntity(id = "emp-server-1", name = "Sam Server", pinHash = h.hash("2222"), role = "SERVER", active = true, email = "sam@gardenbistro.com", phone = "312-555-1002", createdAt = now))
        employeeDao.upsert(EmployeeEntity(id = "emp-server-2", name = "Jordan Server", pinHash = h.hash("3333"), role = "SERVER", active = true, email = "jordan@gardenbistro.com", phone = "312-555-1003", createdAt = now))
        employeeDao.upsert(EmployeeEntity(id = "emp-host-1", name = "Casey Host", pinHash = h.hash("4444"), role = "HOST", active = true, email = "casey@gardenbistro.com", phone = "312-555-1004", createdAt = now))
        employeeDao.upsert(EmployeeEntity(id = "emp-bartender", name = "Riley Bartender", pinHash = h.hash("5555"), role = "BARTENDER", active = true, email = "riley@gardenbistro.com", phone = "312-555-1005", createdAt = now))
        employeeDao.upsert(EmployeeEntity(id = "emp-cashier", name = "Morgan Cashier", pinHash = h.hash("6666"), role = "CASHIER", active = true, email = "morgan@gardenbistro.com", phone = "312-555-1006", createdAt = now))
    }

    private suspend fun seedCustomers() {
        val now = System.currentTimeMillis()
        customerDao.upsert(CustomerEntity(id = "cust-001", name = "Jamie Regular", email = "jamie@example.com", phone = "312-555-2001", loyaltyPoints = 250, storeCreditMinor = 0, marketingOptIn = true, notes = "Favorite: pasta", birthday = "1990-04-15", address = "10 W Lake St", dietaryRestrictions = "vegetarian", createdAt = now, updatedAt = now))
        customerDao.upsert(CustomerEntity(id = "cust-002", name = "Taylor Loyalty", email = "taylor@example.com", phone = "312-555-2002", loyaltyPoints = 1450, storeCreditMinor = 5000, marketingOptIn = true, notes = "", birthday = "1985-12-25", address = "55 N Wabash Ave", dietaryRestrictions = "", createdAt = now, updatedAt = now))
        customerDao.upsert(CustomerEntity(id = "cust-003", name = "Pat Walkin", email = null, phone = "312-555-2003", loyaltyPoints = 0, storeCreditMinor = 0, marketingOptIn = false, notes = "Walk-in", birthday = null, address = null, dietaryRestrictions = "", createdAt = now, updatedAt = now))
        customerDao.upsert(CustomerEntity(id = "cust-004", name = "Morgan Foodie", email = "morgan@example.com", phone = "312-555-2004", loyaltyPoints = 75, storeCreditMinor = 0, marketingOptIn = true, notes = "Allergic to shellfish", birthday = "1992-07-04", address = "200 N Michigan", dietaryRestrictions = "shellfish", createdAt = now, updatedAt = now))
        customerDao.upsert(CustomerEntity(id = "cust-005", name = "Riley Celebration", email = "riley@example.com", phone = "312-555-2005", loyaltyPoints = 320, storeCreditMinor = 0, marketingOptIn = true, notes = "Anniversary 9/15", birthday = "1988-09-15", address = "12 E Adams", dietaryRestrictions = "", createdAt = now, updatedAt = now))
    }

    private suspend fun seedMenu() {
        val cats = listOf(
            CategoryEntity("cat-app", "Appetizers", null, 1, "appetizers", 0xFFFF8A65),
            CategoryEntity("cat-entrees", "Entrées", null, 2, "entrees", 0xFF42A5F5),
            CategoryEntity("cat-pizza", "Pizza", null, 3, "pizza", 0xFFFFCA28),
            CategoryEntity("cat-salads", "Salads", null, 4, "salads", 0xFF66BB6A),
            CategoryEntity("cat-sides", "Sides", null, 5, "sides", 0xFF8D6E63),
            CategoryEntity("cat-desserts", "Desserts", null, 6, "desserts", 0xFFEC407A),
            CategoryEntity("cat-drinks", "Drinks", null, 7, "drinks", 0xFF26C6DA),
            CategoryEntity("cat-bar", "Bar", null, 8, "bar", 0xFFAB47BC),
            CategoryEntity("cat-retail", "Retail", null, 9, "retail", 0xFF607D8B)
        )
        catalogDao.upsertCategories(cats)

        val now = System.currentTimeMillis()
        data class Item(
            val name: String, val catId: String, val price: Double, val cost: Double? = null,
            val tax: TaxCategory = TaxCategory.PREPARED_FOOD, val kitchen: String? = null,
            val prepTime: Int = 0, val barcode: String? = null, val desc: String = "",
            val ageRestriction: Boolean = false
        )
        val items = listOf(
            Item("Calamari", "cat-app", 12.0, 5.50, kitchen = "fryer", prepTime = 8, desc = "Crispy fried squid with marinara"),
            Item("Bruschetta", "cat-app", 9.0, 3.20, kitchen = "cold", prepTime = 5),
            Item("Mozzarella Sticks", "cat-app", 8.5, 3.00, kitchen = "fryer", prepTime = 6),
            Item("Spinach Dip", "cat-app", 10.0, 4.00, kitchen = "cold", prepTime = 5),
            Item("Margherita Pizza", "cat-pizza", 14.0, 5.50, kitchen = "pizza", prepTime = 12),
            Item("Pepperoni Pizza", "cat-pizza", 16.0, 6.00, kitchen = "pizza", prepTime = 12),
            Item("Veggie Pizza", "cat-pizza", 15.0, 5.80, kitchen = "pizza", prepTime = 12),
            Item("Caesar Salad", "cat-salads", 9.5, 3.50, kitchen = "cold", prepTime = 5),
            Item("Greek Salad", "cat-salads", 10.5, 4.00, kitchen = "cold", prepTime = 5),
            Item("Cobb Salad", "cat-salads", 11.5, 4.50, kitchen = "cold", prepTime = 6),
            Item("Grilled Salmon", "cat-entrees", 22.0, 9.00, kitchen = "grill", prepTime = 18),
            Item("Ribeye Steak", "cat-entrees", 32.0, 14.00, kitchen = "grill", prepTime = 20),
            Item("Chicken Alfredo", "cat-entrees", 18.0, 6.50, kitchen = "saute", prepTime = 15),
            Item("Mushroom Risotto", "cat-entrees", 17.0, 5.50, kitchen = "saute", prepTime = 18),
            Item("Veggie Burger", "cat-entrees", 15.0, 5.00, kitchen = "grill", prepTime = 12),
            Item("Cheeseburger", "cat-entrees", 14.0, 5.20, kitchen = "grill", prepTime = 10),
            Item("French Fries", "cat-sides", 5.0, 1.20, kitchen = "fryer", prepTime = 5),
            Item("Sweet Potato Fries", "cat-sides", 6.0, 1.50, kitchen = "fryer", prepTime = 5),
            Item("Side Salad", "cat-sides", 4.5, 1.50, kitchen = "cold", prepTime = 3),
            Item("Garlic Bread", "cat-sides", 4.0, 1.00, kitchen = "oven", prepTime = 5),
            Item("Tiramisu", "cat-desserts", 8.0, 3.00, kitchen = "cold", prepTime = 3),
            Item("Cheesecake", "cat-desserts", 7.5, 2.80, kitchen = "cold", prepTime = 3),
            Item("Chocolate Lava Cake", "cat-desserts", 9.0, 3.50, kitchen = "oven", prepTime = 8),
            Item("Coke", "cat-drinks", 3.0, 0.50, tax = TaxCategory.FOOD_GROCERY),
            Item("Diet Coke", "cat-drinks", 3.0, 0.50, tax = TaxCategory.FOOD_GROCERY),
            Item("Lemonade", "cat-drinks", 3.5, 0.80, tax = TaxCategory.FOOD_GROCERY),
            Item("Iced Tea", "cat-drinks", 3.0, 0.60, tax = TaxCategory.FOOD_GROCERY),
            Item("Coffee", "cat-drinks", 3.5, 0.70, tax = TaxCategory.FOOD_GROCERY),
            Item("Sparkling Water", "cat-drinks", 4.0, 0.80, tax = TaxCategory.FOOD_GROCERY),
            Item("House Red (Glass)", "cat-bar", 8.0, 3.00, tax = TaxCategory.STANDARD, ageRestriction = true),
            Item("House White (Glass)", "cat-bar", 8.0, 3.00, tax = TaxCategory.STANDARD, ageRestriction = true),
            Item("Draft Beer", "cat-bar", 6.0, 2.00, tax = TaxCategory.STANDARD, ageRestriction = true),
            Item("Margarita", "cat-bar", 11.0, 4.00, tax = TaxCategory.STANDARD, ageRestriction = true),
            Item("Old Fashioned", "cat-bar", 12.0, 4.50, tax = TaxCategory.STANDARD, ageRestriction = true),
            Item("Mojito", "cat-bar", 11.0, 4.00, tax = TaxCategory.STANDARD, ageRestriction = true),
            Item("Garden T-Shirt (M)", "cat-retail", 25.0, 11.00, tax = TaxCategory.CLOTHING, barcode = "084000000001", prepTime = 0),
            Item("Garden T-Shirt (L)", "cat-retail", 25.0, 11.00, tax = TaxCategory.CLOTHING, barcode = "084000000002", prepTime = 0),
            Item("Garden Hat", "cat-retail", 18.0, 7.00, tax = TaxCategory.CLOTHING, barcode = "084000000003", prepTime = 0),
            Item("Gift Card $25", "cat-retail", 25.0, 25.00, tax = TaxCategory.EXEMPT, barcode = "084000000004", prepTime = 0)
        )

        for (item in items) {
            val productId = "prod-" + item.name.lowercase().replace(" ", "-").replace("(", "").replace(")", "").replace("$", "").replace("/", "-")
            val variantId = "var-" + productId
            catalogDao.upsertProduct(
                ProductEntity(
                    id = productId,
                    name = item.name,
                    description = item.desc,
                    categoryId = item.catId,
                    type = ProductType.PHYSICAL.name,
                    taxCategory = item.tax.name,
                    ageRestriction = (if (item.ageRestriction) AgeRestriction.TWENTY_ONE_PLUS else AgeRestriction.NONE).name,
                    imageUrl = null,
                    defaultVariantId = variantId,
                    tags = "",
                    trackInventory = item.catId == "cat-retail",
                    isAvailable = true,
                    kitchenRoutingKey = item.kitchen,
                    prepTimeMinutes = item.prepTime,
                    updatedAt = now
                )
            )
            catalogDao.upsertVariants(
                listOf(
                    VariantEntity(
                        id = variantId,
                        productId = productId,
                        name = item.name,
                        sku = productId.uppercase().take(12),
                        barcode = item.barcode,
                        priceMinor = (item.price * 100).toLong(),
                        costPriceMinor = item.cost?.let { (it * 100).toLong() },
                        attributesJson = ""
                    )
                )
            )
            if (item.catId == "cat-retail") {
                catalogDao.upsertInventory(
                    InventoryEntity(variantId, "store-demo-001", 50, 0, 5, 10, now)
                )
            }
        }
    }

    private suspend fun seedTables() {
        val storeId = "store-demo-001"
        val sections = listOf("Main" to 8, "Patio" to 6, "Bar" to 8)
        val now = System.currentTimeMillis()
        var tableNum = 1
        val tables = mutableListOf<TableEntity>()
        for ((section, count) in sections) {
            for (i in 1..count) {
                val capacity = when {
                    i % 4 == 0 -> 6
                    i % 3 == 0 -> 4
                    else -> 2
                }
                tables.add(
                    TableEntity(
                        id = "table-${section.lowercase()}-$i",
                        storeId = storeId,
                        name = "${section.take(1)}$tableNum",
                        section = section,
                        capacity = capacity,
                        shape = "ROUND",
                        x = (i % 4) * 200f,
                        y = (i / 4) * 200f,
                        status = "AVAILABLE",
                        currentOrderId = null,
                        currentGuestCount = 0,
                        serverId = null
                    )
                )
                tableNum++
            }
        }
        tables.forEach { tableDao.upsert(it) }
    }

    private suspend fun seedGiftCards() {
        val now = System.currentTimeMillis()
        listOf(
            Triple("gc-001", "1111-2222-3333-4444", 2500L) to "cust-002",
            Triple("gc-002", "5555-6666-7777-8888", 10000L) to null,
            Triple("gc-003", "9999-0000-1111-2222", 750L) to "cust-001"
        ).forEach { (gc, cust) ->
            val (id, code, balance) = gc
            giftCardDao.upsert(
                GiftCardEntity(
                    id = id, code = code, storeId = "store-demo-001",
                    balanceMinor = balance, initialBalanceMinor = balance,
                    issuedAt = now, expiresAt = null, customerId = cust,
                    notes = "Seeded gift card", active = true
                )
            )
        }
    }

    private suspend fun seedPromotions() {
        // Happy Hour 2-5pm: 20% off drinks
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 17)
        val end = cal.timeInMillis

        promotionDao.upsert(
            PromotionEntity(
                id = "promo-happy-hour", name = "Happy Hour 20% Off Drinks", description = "2-5pm daily, all drinks",
                type = PromotionType.HAPPY_HOUR.name, scope = PromotionScope.CATEGORY.name,
                valueMinor = null, percent = 20, buyQty = 0, getQty = 0, freeItemProductId = null,
                categoryIdsCsv = "cat-drinks,cat-bar", productIdsCsv = "",
                startTime = start, endTime = end, daysOfWeekCsv = "1,2,3,4,5",
                priority = 10, requiresCode = false, code = null, active = true,
                maxRedemptions = null, redemptionCount = 0, maxRedemptionsPerCustomer = null
            )
        )

        promotionDao.upsert(
            PromotionEntity(
                id = "promo-eoy10", name = "EOY10 — 10% off entire order", description = "Year-end coupon",
                type = PromotionType.PERCENT_OFF.name, scope = PromotionScope.ORDER.name,
                valueMinor = null, percent = 10, buyQty = 0, getQty = 0, freeItemProductId = null,
                categoryIdsCsv = "", productIdsCsv = "",
                startTime = 0, endTime = Long.MAX_VALUE, daysOfWeekCsv = "",
                priority = 5, requiresCode = true, code = "EOY10", active = true,
                maxRedemptions = 100, redemptionCount = 0, maxRedemptionsPerCustomer = 1
            )
        )

        promotionDao.upsert(
            PromotionEntity(
                id = "promo-bogo-pizza", name = "BOGO Pizza", description = "Buy one get one free pizza (cheapest free)",
                type = PromotionType.BUY_X_GET_Y.name, scope = PromotionScope.PRODUCT.name,
                valueMinor = null, percent = 0, buyQty = 1, getQty = 1, freeItemProductId = null,
                categoryIdsCsv = "", productIdsCsv = "prod-margherita-pizza,prod-pepperoni-pizza,prod-veggie-pizza",
                startTime = 0, endTime = Long.MAX_VALUE, daysOfWeekCsv = "2,3", // Tue/Wed only
                priority = 8, requiresCode = false, code = null, active = true,
                maxRedemptions = null, redemptionCount = 0, maxRedemptionsPerCustomer = null
            )
        )
    }

    private suspend fun seedReservations() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 30); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        listOf(
            ReservationEntity(
                id = "res-001", storeId = "store-demo-001", customerName = "Jamie Regular", customerId = "cust-001",
                phone = "312-555-2001", email = "jamie@example.com", partySize = 4,
                requestedAt = cal.timeInMillis, tableId = null, status = ReservationStatus.CONFIRMED.name,
                notes = "Anniversary — bring candle", dietaryRestrictions = "vegetarian",
                createdAt = now, confirmedAt = now, seatedAt = null, cancelledAt = null, reminderSent = false
            ),
            ReservationEntity(
                id = "res-002", storeId = "store-demo-001", customerName = "Riley Celebration", customerId = "cust-005",
                phone = "312-555-2005", email = "riley@example.com", partySize = 6,
                requestedAt = cal.timeInMillis + 30 * 60 * 1000L, tableId = null, status = ReservationStatus.REQUESTED.name,
                notes = "Birthday — large party", dietaryRestrictions = "",
                createdAt = now, confirmedAt = null, seatedAt = null, cancelledAt = null, reminderSent = false
            ),
            ReservationEntity(
                id = "res-003", storeId = "store-demo-001", customerName = "Pat Walkin", customerId = "cust-003",
                phone = "312-555-2003", email = null, partySize = 2,
                requestedAt = cal.timeInMillis + 90 * 60 * 1000L, tableId = null, status = ReservationStatus.REQUESTED.name,
                notes = "", dietaryRestrictions = "",
                createdAt = now, confirmedAt = null, seatedAt = null, cancelledAt = null, reminderSent = false
            )
        ).forEach { reservationDao.upsert(it) }
    }
}
