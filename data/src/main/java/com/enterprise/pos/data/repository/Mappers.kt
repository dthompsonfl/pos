package com.enterprise.pos.data.repository

import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.data.db.entity.CategoryEntity
import com.enterprise.pos.data.db.entity.CustomerEntity
import com.enterprise.pos.data.db.entity.DiscountEntity
import com.enterprise.pos.data.db.entity.EmployeeEntity
import com.enterprise.pos.data.db.entity.InventoryEntity
import com.enterprise.pos.data.db.entity.OrderEntity
import com.enterprise.pos.data.db.entity.OrderLineEntity
import com.enterprise.pos.data.db.entity.PaymentEntity
import com.enterprise.pos.data.db.entity.ProductEntity
import com.enterprise.pos.data.db.entity.RegisterEntity
import com.enterprise.pos.data.db.entity.StoreEntity
import com.enterprise.pos.data.db.entity.TableEntity
import com.enterprise.pos.data.db.entity.TaxLineEntity
import com.enterprise.pos.data.db.entity.VariantEntity
import com.enterprise.pos.domain.model.AgeRestriction
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.Customer
import com.enterprise.pos.domain.model.Discount
import com.enterprise.pos.domain.model.DiscountType
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductType
import com.enterprise.pos.domain.model.Register
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.RolePermissions
import com.enterprise.pos.domain.model.Store
import com.enterprise.pos.domain.model.TableShape
import com.enterprise.pos.domain.model.TableStatus
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.model.TaxLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

object Mappers {
    private val json = Json { ignoreUnknownKeys = true }

    fun CategoryEntity.toDomain() = Category(
        id = CategoryId(id), name = name, parentId = parentId?.let(::CategoryId),
        displayOrder = displayOrder, iconKey = iconKey, color = color
    )

    fun VariantEntity.toDomain() = com.enterprise.pos.domain.model.ProductVariant(
        id = VariantId(id),
        name = name,
        sku = sku,
        barcode = barcode,
        price = Money.ofMinor(priceMinor),
        costPrice = costPriceMinor?.let(Money::ofMinor),
        attributes = if (attributesJson.isBlank()) emptyMap()
            else json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), attributesJson)
    )

    fun ProductEntity.toDomain(variants: List<com.enterprise.pos.domain.model.ProductVariant>) = Product(
        id = ProductId(id),
        name = name,
        description = description,
        categoryId = CategoryId(categoryId),
        type = runCatching { ProductType.valueOf(type) }.getOrDefault(ProductType.PHYSICAL),
        taxCategory = runCatching { TaxCategory.valueOf(taxCategory) }.getOrDefault(TaxCategory.STANDARD),
        ageRestriction = runCatching { AgeRestriction.valueOf(ageRestriction) }.getOrDefault(AgeRestriction.NONE),
        imageUrl = imageUrl,
        defaultVariantId = defaultVariantId?.let(::VariantId),
        variants = variants,
        tags = if (tags.isBlank()) emptyList() else tags.split(","),
        trackInventory = trackInventory,
        isAvailable = isAvailable,
        kitchenRoutingKey = kitchenRoutingKey,
        prepTimeMinutes = prepTimeMinutes
    )

    fun InventoryEntity.toDomain() = InventorySnapshot(
        variantId = VariantId(variantId),
        storeId = StoreId(storeId),
        onHand = onHand,
        committed = committed,
        available = onHand - committed,
        lowStockThreshold = lowStockThreshold,
        reorderPoint = reorderPoint
    )

    fun OrderEntity.toDomain(lines: List<OrderLineEntity>, taxLines: List<TaxLineEntity>, discounts: List<DiscountEntity>, payments: List<PaymentEntity> = emptyList(), refunds: List<PaymentEntity> = emptyList()): Order = Order(
        id = OrderId(id),
        storeId = StoreId(storeId),
        registerId = RegisterId(registerId),
        employeeId = EmployeeId(employeeId),
        customerId = customerId?.let(::CustomerId),
        diningMode = runCatching { DiningMode.valueOf(diningMode) }.getOrDefault(DiningMode.RETAIL),
        tableId = tableId?.let(::TableId),
        tableName = tableName,
        guestCount = guestCount,
        status = runCatching { OrderStatus.valueOf(status) }.getOrDefault(OrderStatus.DRAFT),
        lines = lines.map { it.toDomain() },
        discounts = discounts.map { it.toDomain() },
        orderLevelDiscount = Money.ofMinor(orderLevelDiscountMinor),
        taxLines = taxLines.map { it.toDomain() },
        tip = Money.ofMinor(tipMinor),
        serviceCharges = Money.ofMinor(serviceChargesMinor),
        payments = payments.filter { it.refundedAmountMinor == 0L }.map { it.toDomain() },
        refunds = payments.filter { it.refundedAmountMinor > 0L }.map { it.copy(amountMinor = it.refundedAmountMinor).toDomain() },
        taxExempt = taxExempt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        closedAt = closedAt,
        notes = notes,
        deliveryAddress = deliveryAddress,
        deliveryProvider = deliveryProvider
    )

    fun OrderLineEntity.toDomain(): OrderLine = OrderLine(
        id = com.enterprise.pos.core.OrderLineId(id),
        parentLineId = parentLineId?.let { com.enterprise.pos.core.OrderLineId(it) },
        lineType = runCatching { OrderLineType.valueOf(lineType) }.getOrDefault(OrderLineType.ITEM),
        productId = productId?.let(::ProductId),
        variantId = variantId?.let(::VariantId),
        name = name,
        quantity = com.enterprise.pos.core.Quantity.of(quantity),
        unitPrice = Money.ofMinor(unitPriceMinor),
        discount = Money.ofMinor(discountMinor),
        notes = notes,
        kitchenRoutingKey = kitchenRoutingKey,
        sentToKitchen = sentToKitchen,
        taxCategory = runCatching { TaxCategory.valueOf(taxCategory) }.getOrDefault(TaxCategory.STANDARD),
        taxAmount = Money.ofMinor(taxAmountMinor)
    )

    fun TaxLineEntity.toDomain(): TaxLine = TaxLine(
        name = name,
        rate = Percent.ofBasisPoints(rateBasisPoints),
        amount = Money.ofMinor(amountMinor),
        taxCategory = runCatching { TaxCategory.valueOf(taxCategory) }.getOrDefault(TaxCategory.STANDARD)
    )

    fun DiscountEntity.toDomain(): Discount = Discount(
        id = discountId,
        name = name,
        type = runCatching { DiscountType.valueOf(type) }.getOrDefault(DiscountType.PERCENTAGE),
        value = valueMinor?.let(Money::ofMinor),
        percent = Percent.ofBasisPoints(percentBasisPoints),
        requiresManagerApproval = requiresManagerApproval
    )

    fun Order.toEntity(): OrderEntity = OrderEntity(
        id = id.value,
        storeId = storeId.value,
        registerId = registerId.value,
        employeeId = employeeId.value,
        customerId = customerId?.value,
        diningMode = diningMode.name,
        tableId = tableId?.value,
        tableName = tableName,
        guestCount = guestCount,
        status = status.name,
        orderLevelDiscountMinor = orderLevelDiscount.minorUnits,
        tipMinor = tip.minorUnits,
        serviceChargesMinor = serviceCharges.minorUnits,
        taxExempt = taxExempt,
        notes = notes,
        deliveryAddress = deliveryAddress,
        deliveryProvider = deliveryProvider,
        createdAt = createdAt,
        updatedAt = updatedAt,
        closedAt = closedAt
    )

    fun OrderLine.toEntity(orderId: OrderId, displayOrder: Int): OrderLineEntity = OrderLineEntity(
        id = id.value,
        orderId = orderId.value,
        parentLineId = parentLineId?.value,
        lineType = lineType.name,
        productId = productId?.value,
        variantId = variantId?.value,
        name = name,
        quantity = quantity.asDouble,
        unitPriceMinor = unitPrice.minorUnits,
        discountMinor = discount.minorUnits,
        notes = notes,
        kitchenRoutingKey = kitchenRoutingKey,
        sentToKitchen = sentToKitchen,
        displayOrder = displayOrder,
        taxCategory = taxCategory.name,
        taxAmountMinor = taxAmount.minorUnits
    )

    fun TaxLine.toEntity(orderId: OrderId): TaxLineEntity = TaxLineEntity(
        orderId = orderId.value,
        name = name,
        rateBasisPoints = rate.basisPoints,
        amountMinor = amount.minorUnits,
        taxCategory = taxCategory.name
    )

    fun Discount.toEntity(orderId: OrderId): DiscountEntity = DiscountEntity(
        orderId = orderId.value,
        discountId = id,
        name = name,
        type = type.name,
        valueMinor = value?.minorUnits,
        percentBasisPoints = percent.basisPoints,
        requiresManagerApproval = requiresManagerApproval
    )

    fun CustomerEntity.toDomain(): Customer = Customer(
        id = CustomerId(id),
        name = name,
        email = email,
        phone = phone,
        loyaltyPoints = loyaltyPoints,
        storeCredit = Money.ofMinor(storeCreditMinor),
        marketingOptIn = marketingOptIn,
        notes = notes,
        birthday = birthday,
        address = address,
        dietaryRestrictions = if (dietaryRestrictions.isBlank()) emptyList() else dietaryRestrictions.split(","),
        createdAt = createdAt
    )

    fun Customer.toEntity(now: Long): CustomerEntity = CustomerEntity(
        id = id.value,
        name = name,
        email = email,
        phone = phone,
        loyaltyPoints = loyaltyPoints,
        storeCreditMinor = storeCredit.minorUnits,
        marketingOptIn = marketingOptIn,
        notes = notes,
        birthday = birthday,
        address = address,
        dietaryRestrictions = dietaryRestrictions.joinToString(","),
        createdAt = createdAt,
        updatedAt = now
    )

    fun EmployeeEntity.toDomain(): Employee = Employee(
        id = EmployeeId(id),
        name = name,
        pinHash = pinHash,
        role = runCatching { EmployeeRole.valueOf(role) }.getOrDefault(EmployeeRole.CASHIER),
        active = active,
        email = email,
        phone = phone,
        failedLoginAttempts = failedLoginAttempts,
        lockedUntil = lockedUntil,
        lastLoginAt = lastLoginAt
    )

    fun Employee.toEntity(now: Long): EmployeeEntity = EmployeeEntity(
        id = id.value,
        name = name,
        pinHash = pinHash,
        role = role.name,
        active = active,
        email = email,
        phone = phone,
        createdAt = now,
        failedLoginAttempts = failedLoginAttempts,
        lockedUntil = lockedUntil,
        lastLoginAt = lastLoginAt
    )

    fun StoreEntity.toDomain(): Store = Store(
        id = StoreId(id),
        name = name,
        address = address,
        phone = phone,
        taxIdentifier = taxIdentifier,
        currency = currency,
        timezone = timezone
    )

    fun RegisterEntity.toDomain(): Register = Register(
        id = RegisterId(id),
        storeId = StoreId(storeId),
        name = name,
        deviceIdentifier = deviceIdentifier,
        active = active
    )

    fun TableEntity.toDomain(): RestaurantTable = RestaurantTable(
        id = TableId(id),
        storeId = StoreId(storeId),
        name = name,
        section = section,
        capacity = capacity,
        shape = runCatching { TableShape.valueOf(shape) }.getOrDefault(TableShape.ROUND),
        x = x, y = y,
        status = runCatching { TableStatus.valueOf(status) }.getOrDefault(TableStatus.AVAILABLE),
        currentOrderId = currentOrderId?.let(::OrderId),
        currentGuestCount = currentGuestCount,
        serverId = serverId?.let(::EmployeeId)
    )

    fun PaymentEntity.toDomain(): com.enterprise.pos.domain.model.Payment = com.enterprise.pos.domain.model.Payment(
        id = com.enterprise.pos.core.PaymentId(id),
        orderId = OrderId(orderId),
        provider = provider,
        providerTransactionId = providerTransactionId,
        amount = Money.ofMinor(amountMinor),
        currency = currency,
        cardBrand = cardBrand,
        last4 = last4,
        entryMode = entryMode,
        receiptUrl = receiptUrl,
        capturedAt = capturedAt,
        refundedAmount = Money.ofMinor(refundedAmountMinor)
    )

    fun rolePermissions(role: EmployeeRole): RolePermissions = when (role) {
        EmployeeRole.CASHIER -> RolePermissions(role, canApplyDiscounts = true, maxDiscountPercent = 5, canOpenDrawer = true)
        EmployeeRole.SERVER -> RolePermissions(role, canApplyDiscounts = true, maxDiscountPercent = 10, canCompItems = true, maxCompValue = Money.of(25.0))
        EmployeeRole.HOST -> RolePermissions(role, canApplyDiscounts = false)
        EmployeeRole.BARTENDER -> RolePermissions(role, canApplyDiscounts = true, maxDiscountPercent = 15, canOpenDrawer = true, canCompItems = true, maxCompValue = Money.of(50.0))
        EmployeeRole.LINE_COOK -> RolePermissions(role)
        EmployeeRole.KITCHEN_LEAD -> RolePermissions(role, canApplyDiscounts = true, maxDiscountPercent = 25)
        EmployeeRole.SHIFT_LEAD -> RolePermissions(role, canApplyDiscounts = true, maxDiscountPercent = 25, canProcessRefunds = true, canVoidOrders = true, canOpenDrawer = true, canViewReports = true, canCompItems = true, maxCompValue = Money.of(75.0))
        EmployeeRole.MANAGER -> RolePermissions(role, canProcessRefunds = true, canApplyDiscounts = true, maxDiscountPercent = 100, canVoidOrders = true, canOpenDrawer = true, canManageEmployees = true, canViewReports = true, canManageInventory = true, canCompItems = true, maxCompValue = Money.of(500.0))
        EmployeeRole.ADMIN -> RolePermissions(role, canProcessRefunds = true, canApplyDiscounts = true, maxDiscountPercent = 100, canVoidOrders = true, canOpenDrawer = true, canManageEmployees = true, canViewReports = true, canManageInventory = true, canCompItems = true, maxCompValue = Money.of(10_000.0))
    }
}
