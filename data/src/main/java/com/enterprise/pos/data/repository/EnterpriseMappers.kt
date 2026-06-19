package com.enterprise.pos.data.repository

import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.data.db.entity.AuditLogEntity
import com.enterprise.pos.data.db.entity.GiftCardEntity
import com.enterprise.pos.data.db.entity.GiftCardTransactionEntity
import com.enterprise.pos.data.db.entity.InventoryAdjustmentEntity
import com.enterprise.pos.data.db.entity.InventoryTransferEntity
import com.enterprise.pos.data.db.entity.LoyaltyRewardEntity
import com.enterprise.pos.data.db.entity.MigrationJobEntity
import com.enterprise.pos.data.db.entity.PromotionEntity
import com.enterprise.pos.data.db.entity.ReservationEntity
import com.enterprise.pos.data.db.entity.ReturnEntity
import com.enterprise.pos.data.db.entity.SettingEntity
import com.enterprise.pos.data.db.entity.ShiftEntity
import com.enterprise.pos.data.db.entity.TipLogEntity
import com.enterprise.pos.data.db.entity.ZReportEntity
import com.enterprise.pos.domain.model.AbcAnalysis
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.domain.model.AuditAction
import com.enterprise.pos.domain.model.AuditLogEntry
import com.enterprise.pos.domain.model.DashboardAlert
import com.enterprise.pos.domain.model.DashboardSnapshot
import com.enterprise.pos.domain.model.GiftCard
import com.enterprise.pos.domain.model.GiftCardTransaction
import com.enterprise.pos.domain.model.GiftCardTxType
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.enterprise.pos.domain.model.InventoryTransfer
import com.enterprise.pos.domain.model.LoyaltyReward
import com.enterprise.pos.domain.model.LoyaltyRewardType
import com.enterprise.pos.domain.model.MigrationConflict
import com.enterprise.pos.domain.model.MigrationJob
import com.enterprise.pos.domain.model.MigrationSource
import com.enterprise.pos.domain.model.MigrationStatus
import com.enterprise.pos.domain.model.MigrationType
import com.enterprise.pos.domain.model.Promotion
import com.enterprise.pos.domain.model.PromotionScope
import com.enterprise.pos.domain.model.PromotionType
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.ReturnLine
import com.enterprise.pos.domain.model.ReturnRequest
import com.enterprise.pos.domain.model.ReturnStatus
import com.enterprise.pos.domain.model.SalesByCategory
import com.enterprise.pos.domain.model.SalesByEmployee
import com.enterprise.pos.domain.model.SalesByHour
import com.enterprise.pos.domain.model.Shift
import com.enterprise.pos.domain.model.ShiftStatus
import com.enterprise.pos.domain.model.TenderSplit
import com.enterprise.pos.domain.model.TipPoolSummary
import com.enterprise.pos.domain.model.TipPoolType
import com.enterprise.pos.domain.model.TransferLine
import com.enterprise.pos.domain.model.TransferStatus
import com.enterprise.pos.domain.model.ZReport
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object EnterpriseMappers {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val strListSerializer = ListSerializer(String.serializer())
    private val intMapSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val longMapSerializer = MapSerializer(String.serializer(), Long.serializer())
    private val stringStringMap = MapSerializer(String.serializer(), String.serializer())

    fun ReservationEntity.toDomain(): Reservation = Reservation(
        id = Id(id),
        storeId = StoreId(storeId),
        customerName = customerName,
        customerId = customerId?.let(::CustomerId),
        phone = phone,
        email = email,
        partySize = partySize,
        requestedAt = requestedAt,
        tableId = tableId?.let(::TableId),
        status = runCatching { ReservationStatus.valueOf(status) }.getOrDefault(ReservationStatus.REQUESTED),
        notes = notes,
        dietaryRestrictions = if (dietaryRestrictions.isBlank()) emptyList() else json.decodeFromString(strListSerializer, dietaryRestrictions),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        seatedAt = seatedAt,
        cancelledAt = cancelledAt,
        reminderSent = reminderSent
    )

    fun Reservation.toEntity(): ReservationEntity = ReservationEntity(
        id = id.value,
        storeId = storeId.value,
        customerName = customerName,
        customerId = customerId?.value,
        phone = phone,
        email = email,
        partySize = partySize,
        requestedAt = requestedAt,
        tableId = tableId?.value,
        status = status.name,
        notes = notes,
        dietaryRestrictions = if (dietaryRestrictions.isEmpty()) "" else json.encodeToString(strListSerializer, dietaryRestrictions),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        seatedAt = seatedAt,
        cancelledAt = cancelledAt,
        reminderSent = reminderSent
    )

    fun GiftCardEntity.toDomain(): GiftCard = GiftCard(
        id = Id(id),
        code = code,
        storeId = StoreId(storeId),
        balance = Money.ofMinor(balanceMinor),
        initialBalance = Money.ofMinor(initialBalanceMinor),
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        customerId = customerId?.let(::CustomerId),
        notes = notes,
        active = active
    )

    fun GiftCardTransactionEntity.toDomain(): GiftCardTransaction = GiftCardTransaction(
        id = Id(id),
        giftCardId = Id(giftCardId),
        orderId = orderId?.let(::OrderId),
        amount = Money.ofMinor(amountMinor),
        balanceAfter = Money.ofMinor(balanceAfterMinor),
        timestamp = timestamp,
        employeeId = EmployeeId(employeeId),
        type = runCatching { GiftCardTxType.valueOf(type) }.getOrDefault(GiftCardTxType.REDEEM)
    )

    fun PromotionEntity.toDomain(): Promotion = Promotion(
        id = Id(id),
        name = name,
        description = description,
        type = runCatching { PromotionType.valueOf(type) }.getOrDefault(PromotionType.PERCENT_OFF),
        scope = runCatching { PromotionScope.valueOf(scope) }.getOrDefault(PromotionScope.ORDER),
        value = valueMinor?.let(Money::ofMinor),
        percent = percent,
        buyQty = buyQty,
        getQty = getQty,
        freeItemProductId = freeItemProductId?.let { com.enterprise.pos.core.ProductId(it) },
        categoryIds = if (categoryIdsCsv.isBlank()) emptyList() else categoryIdsCsv.split(",").map { com.enterprise.pos.core.CategoryId(it) },
        productIds = if (productIdsCsv.isBlank()) emptyList() else productIdsCsv.split(",").map { com.enterprise.pos.core.ProductId(it) },
        startTime = startTime,
        endTime = endTime,
        daysOfWeek = if (daysOfWeekCsv.isBlank()) emptySet() else daysOfWeekCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet(),
        priority = priority,
        requiresCode = requiresCode,
        code = code,
        active = active,
        maxRedemptions = maxRedemptions,
        redemptionCount = redemptionCount,
        maxRedemptionsPerCustomer = maxRedemptionsPerCustomer
    )

    fun Promotion.toEntity(): PromotionEntity = PromotionEntity(
        id = id.value,
        name = name,
        description = description,
        type = type.name,
        scope = scope.name,
        valueMinor = value?.minorUnits,
        percent = percent,
        buyQty = buyQty,
        getQty = getQty,
        freeItemProductId = freeItemProductId?.value,
        categoryIdsCsv = categoryIds.joinToString(",") { it.value },
        productIdsCsv = productIds.joinToString(",") { it.value },
        startTime = startTime,
        endTime = endTime,
        daysOfWeekCsv = daysOfWeek.joinToString(","),
        priority = priority,
        requiresCode = requiresCode,
        code = code,
        active = active,
        maxRedemptions = maxRedemptions,
        redemptionCount = redemptionCount,
        maxRedemptionsPerCustomer = maxRedemptionsPerCustomer
    )

    fun LoyaltyRewardEntity.toDomain(): LoyaltyReward = LoyaltyReward(
        id = Id(id),
        name = name,
        pointsCost = pointsCost,
        rewardType = runCatching { LoyaltyRewardType.valueOf(rewardType) }.getOrDefault(LoyaltyRewardType.STORE_CREDIT),
        value = valueMinor?.let(Money::ofMinor),
        freeItemProductId = freeItemProductId?.let { com.enterprise.pos.core.ProductId(it) },
        active = active
    )

    fun ShiftEntity.toDomain(): Shift = Shift(
        id = Id(id),
        storeId = StoreId(storeId),
        registerId = RegisterId(registerId),
        employeeId = EmployeeId(employeeId),
        status = runCatching { ShiftStatus.valueOf(status) }.getOrDefault(ShiftStatus.OPEN),
        startedAt = startedAt,
        endedAt = endedAt,
        startingCash = Money.ofMinor(startingCashMinor),
        expectedCash = Money.ofMinor(expectedCashMinor),
        countedCash = countedCashMinor?.let(Money::ofMinor),
        cashVariance = Money.ofMinor(cashVarianceMinor),
        startingFloat = if (startingFloatJson.isBlank()) emptyMap() else json.decodeFromString(intMapSerializer, startingFloatJson).mapKeys { it.key.toInt() },
        countedFloat = if (countedFloatJson.isBlank()) emptyMap() else json.decodeFromString(intMapSerializer, countedFloatJson).mapKeys { it.key.toInt() },
        notes = notes,
        salesTotal = Money.ofMinor(salesTotalMinor),
        tipsCollected = Money.ofMinor(tipsCollectedMinor),
        refundsTotal = Money.ofMinor(refundsTotalMinor),
        payoutsTotal = Money.ofMinor(payoutsTotalMinor),
        transactionCount = transactionCount
    )

    fun Shift.toEntity(): ShiftEntity = ShiftEntity(
        id = id.value,
        storeId = storeId.value,
        registerId = registerId.value,
        employeeId = employeeId.value,
        status = status.name,
        startedAt = startedAt,
        endedAt = endedAt,
        startingCashMinor = startingCash.minorUnits,
        expectedCashMinor = expectedCash.minorUnits,
        countedCashMinor = countedCash?.minorUnits,
        cashVarianceMinor = cashVariance.minorUnits,
        startingFloatJson = if (startingFloat.isEmpty()) "" else json.encodeToString(intMapSerializer, startingFloat.mapKeys { it.key.toString() }),
        countedFloatJson = if (countedFloat.isEmpty()) "" else json.encodeToString(intMapSerializer, countedFloat.mapKeys { it.key.toString() }),
        notes = notes,
        salesTotalMinor = salesTotal.minorUnits,
        tipsCollectedMinor = tipsCollected.minorUnits,
        refundsTotalMinor = refundsTotal.minorUnits,
        payoutsTotalMinor = payoutsTotal.minorUnits,
        transactionCount = transactionCount
    )

    fun ZReportEntity.toDomain(): ZReport = ZReport(
        id = Id(id),
        storeId = StoreId(storeId),
        registerId = RegisterId(registerId),
        shiftId = Id(shiftId),
        generatedAt = generatedAt,
        periodStart = periodStart,
        periodEnd = periodEnd,
        grossSales = Money.ofMinor(grossSalesMinor),
        returns = Money.ofMinor(returnsMinor),
        discounts = Money.ofMinor(discountsMinor),
        netSales = Money.ofMinor(netSalesMinor),
        taxCollected = if (taxCollectedJson.isBlank()) emptyMap() else json.decodeFromString(longMapSerializer, taxCollectedJson).mapValues { Money.ofMinor(it.value) },
        tips = Money.ofMinor(tipsMinor),
        cashTotal = Money.ofMinor(cashTotalMinor),
        cardTotal = Money.ofMinor(cardTotalMinor),
        otherTenders = if (otherTendersJson.isBlank()) emptyMap() else json.decodeFromString(longMapSerializer, otherTendersJson).mapValues { Money.ofMinor(it.value) },
        overShort = Money.ofMinor(overShortMinor),
        transactionCount = transactionCount,
        refundCount = refundCount,
        voidCount = voidCount,
        noSaleCount = noSaleCount,
        employeeBreakdown = if (employeeBreakdownJson.isBlank()) emptyMap() else json.decodeFromString(longMapSerializer, employeeBreakdownJson).mapKeys { EmployeeId(it.key) }.mapValues { Money.ofMinor(it.value) }
    )

    fun ZReport.toEntity(): ZReportEntity = ZReportEntity(
        id = id.value,
        storeId = storeId.value,
        registerId = registerId.value,
        shiftId = shiftId.value,
        generatedAt = generatedAt,
        periodStart = periodStart,
        periodEnd = periodEnd,
        grossSalesMinor = grossSales.minorUnits,
        returnsMinor = returns.minorUnits,
        discountsMinor = discounts.minorUnits,
        netSalesMinor = netSales.minorUnits,
        taxCollectedJson = if (taxCollected.isEmpty()) "" else json.encodeToString(longMapSerializer, taxCollected.mapValues { it.value.minorUnits }),
        tipsMinor = tips.minorUnits,
        cashTotalMinor = cashTotal.minorUnits,
        cardTotalMinor = cardTotal.minorUnits,
        otherTendersJson = if (otherTenders.isEmpty()) "" else json.encodeToString(longMapSerializer, otherTenders.mapValues { it.value.minorUnits }),
        overShortMinor = overShort.minorUnits,
        transactionCount = transactionCount,
        refundCount = refundCount,
        voidCount = voidCount,
        noSaleCount = noSaleCount,
        employeeBreakdownJson = if (employeeBreakdown.isEmpty()) "" else json.encodeToString(longMapSerializer, employeeBreakdown.mapKeys { it.key.value }.mapValues { it.value.minorUnits })
    )

    fun InventoryAdjustmentEntity.toDomain(): InventoryAdjustment = InventoryAdjustment(
        id = Id(id),
        variantId = com.enterprise.pos.core.VariantId(variantId),
        storeId = StoreId(storeId),
        delta = delta,
        reason = runCatching { AdjustmentReason.valueOf(reason) }.getOrDefault(AdjustmentReason.OTHER),
        notes = notes,
        employeeId = EmployeeId(employeeId),
        timestamp = timestamp,
        unitCost = unitCostMinor?.let(Money::ofMinor)
    )

    fun InventoryTransferEntity.toDomain(): InventoryTransfer {
        val items: List<TransferLine> = if (itemsJson.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(TransferLine.serializer()), itemsJson)
        return InventoryTransfer(
            id = Id(id),
            fromStoreId = StoreId(fromStoreId),
            toStoreId = StoreId(toStoreId),
            status = runCatching { TransferStatus.valueOf(status) }.getOrDefault(TransferStatus.DRAFT),
            items = items,
            createdBy = EmployeeId(createdBy),
            createdAt = createdAt,
            shippedAt = shippedAt,
            receivedAt = receivedAt,
            notes = notes
        )
    }

    fun InventoryTransfer.toEntity(): InventoryTransferEntity = InventoryTransferEntity(
        id = id.value,
        fromStoreId = fromStoreId.value,
        toStoreId = toStoreId.value,
        status = status.name,
        itemsJson = json.encodeToString(ListSerializer(TransferLine.serializer()), items),
        createdBy = createdBy.value,
        createdAt = createdAt,
        shippedAt = shippedAt,
        receivedAt = receivedAt,
        notes = notes
    )

    fun ReturnEntity.toDomain(): ReturnRequest {
        val lines: List<ReturnLine> = if (linesJson.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(ReturnLine.serializer()), linesJson)
        val tenders: List<TenderSplit> = if (refundTendersJson.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(TenderSplit.serializer()), refundTendersJson)
        return ReturnRequest(
            id = Id(id),
            originalOrderId = OrderId(originalOrderId),
            storeId = StoreId(storeId),
            employeeId = EmployeeId(employeeId),
            lines = lines,
            totalRefund = Money.ofMinor(totalRefundMinor),
            reason = reason,
            timestamp = timestamp,
            refundTenders = tenders,
            status = runCatching { ReturnStatus.valueOf(status) }.getOrDefault(ReturnStatus.COMPLETED)
        )
    }

    fun ReturnRequest.toEntity(): ReturnEntity = ReturnEntity(
        id = id.value,
        originalOrderId = originalOrderId.value,
        storeId = storeId.value,
        employeeId = employeeId.value,
        linesJson = json.encodeToString(ListSerializer(ReturnLine.serializer()), lines),
        totalRefundMinor = totalRefund.minorUnits,
        reason = reason,
        timestamp = timestamp,
        refundTendersJson = json.encodeToString(ListSerializer(TenderSplit.serializer()), refundTenders),
        status = status.name
    )

    fun AuditLogEntity.toDomain(): AuditLogEntry = AuditLogEntry(
        id = Id(id),
        storeId = StoreId(storeId),
        registerId = registerId?.let(::RegisterId),
        employeeId = EmployeeId(employeeId),
        employeeName = employeeName,
        action = runCatching { AuditAction.valueOf(action) }.getOrDefault(AuditAction.ORDER_CREATED),
        entityType = entityType,
        entityId = entityId,
        beforeJson = beforeJson,
        afterJson = afterJson,
        reason = reason,
        timestamp = timestamp,
        ipAddress = ipAddress,
        deviceIdentifier = deviceIdentifier
    )

    fun AuditLogEntry.toEntity(): AuditLogEntity = AuditLogEntity(
        id = id.value,
        storeId = storeId.value,
        registerId = registerId?.value,
        employeeId = employeeId.value,
        employeeName = employeeName,
        action = action.name,
        entityType = entityType,
        entityId = entityId,
        beforeJson = beforeJson,
        afterJson = afterJson,
        reason = reason,
        timestamp = timestamp,
        ipAddress = ipAddress,
        deviceIdentifier = deviceIdentifier
    )

    fun MigrationJobEntity.toDomain(): MigrationJob {
        val conflicts: List<MigrationConflict> = if (conflictsJson.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(MigrationConflict.serializer()), conflictsJson)
        return MigrationJob(
            id = Id(id),
            source = runCatching { MigrationSource.valueOf(source) }.getOrDefault(MigrationSource.OTHER),
            type = runCatching { MigrationType.valueOf(type) }.getOrDefault(MigrationType.ALL),
            status = runCatching { MigrationStatus.valueOf(status) }.getOrDefault(MigrationStatus.PENDING),
            totalRecords = totalRecords,
            processedRecords = processedRecords,
            failedRecords = failedRecords,
            startedAt = startedAt,
            completedAt = completedAt,
            errorMessage = errorMessage,
            configJson = configJson,
            createdBy = EmployeeId(createdBy),
            createdAt = createdAt,
            conflicts = conflicts
        )
    }

    fun MigrationJob.toEntity(): MigrationJobEntity = MigrationJobEntity(
        id = id.value,
        source = source.name,
        type = type.name,
        status = status.name,
        totalRecords = totalRecords,
        processedRecords = processedRecords,
        failedRecords = failedRecords,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        configJson = configJson,
        createdBy = createdBy.value,
        createdAt = createdAt,
        conflictsJson = if (conflicts.isEmpty()) "" else json.encodeToString(ListSerializer(MigrationConflict.serializer()), conflicts)
    )
}
