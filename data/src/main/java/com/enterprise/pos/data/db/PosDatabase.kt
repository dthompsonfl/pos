package com.enterprise.pos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.enterprise.pos.data.db.dao.AuditLogDao
import com.enterprise.pos.data.db.dao.CatalogDao
import com.enterprise.pos.data.db.dao.CustomerDao
import com.enterprise.pos.data.db.dao.EmployeeDao
import com.enterprise.pos.data.db.dao.GiftCardDao
import com.enterprise.pos.data.db.dao.InventoryAdjustmentDao
import com.enterprise.pos.data.db.dao.InventoryTransferDao
import com.enterprise.pos.data.db.dao.LoyaltyRewardDao
import com.enterprise.pos.data.db.dao.MigrationJobDao
import com.enterprise.pos.data.db.dao.OrderDao
import com.enterprise.pos.data.db.dao.PaymentDao
import com.enterprise.pos.data.db.dao.PromotionDao
import com.enterprise.pos.data.db.dao.ReservationDao
import com.enterprise.pos.data.db.dao.ReturnDao
import com.enterprise.pos.data.db.dao.SettingDao
import com.enterprise.pos.data.db.dao.ShiftDao
import com.enterprise.pos.data.db.dao.StoreDao
import com.enterprise.pos.data.db.dao.SyncQueueDao
import com.enterprise.pos.data.db.dao.TableDao
import com.enterprise.pos.data.db.dao.TipLogDao
import com.enterprise.pos.data.db.dao.ZReportDao
import com.enterprise.pos.data.db.entity.AuditLogEntity
import com.enterprise.pos.data.db.entity.CategoryEntity
import com.enterprise.pos.data.db.entity.CustomerEntity
import com.enterprise.pos.data.db.entity.DiscountEntity
import com.enterprise.pos.data.db.entity.EmployeeEntity
import com.enterprise.pos.data.db.entity.GiftCardEntity
import com.enterprise.pos.data.db.entity.GiftCardTransactionEntity
import com.enterprise.pos.data.db.entity.InventoryAdjustmentEntity
import com.enterprise.pos.data.db.entity.InventoryEntity
import com.enterprise.pos.data.db.entity.InventoryTransferEntity
import com.enterprise.pos.data.db.entity.LoyaltyRewardEntity
import com.enterprise.pos.data.db.entity.MigrationJobEntity
import com.enterprise.pos.data.db.entity.ModifierGroupEntity
import com.enterprise.pos.data.db.entity.OrderEntity
import com.enterprise.pos.data.db.entity.OrderLineEntity
import com.enterprise.pos.data.db.entity.PaymentEntity
import com.enterprise.pos.data.db.entity.ProductEntity
import com.enterprise.pos.data.db.entity.PromotionEntity
import com.enterprise.pos.data.db.entity.RegisterEntity
import com.enterprise.pos.data.db.entity.ReservationEntity
import com.enterprise.pos.data.db.entity.ReturnEntity
import com.enterprise.pos.data.db.entity.SettingEntity
import com.enterprise.pos.data.db.entity.ShiftEntity
import com.enterprise.pos.data.db.entity.StoreEntity
import com.enterprise.pos.data.db.entity.SyncQueueEntity
import com.enterprise.pos.data.sync.SyncOutboxDao
import com.enterprise.pos.data.sync.SyncOutboxEntity
import com.enterprise.pos.data.db.entity.TableEntity
import com.enterprise.pos.data.db.entity.TaxLineEntity
import com.enterprise.pos.data.db.entity.TipLogEntity
import com.enterprise.pos.data.db.entity.VariantEntity
import com.enterprise.pos.data.db.entity.ZReportEntity

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        VariantEntity::class,
        InventoryEntity::class,
        ModifierGroupEntity::class,
        OrderEntity::class,
        OrderLineEntity::class,
        TaxLineEntity::class,
        DiscountEntity::class,
        CustomerEntity::class,
        EmployeeEntity::class,
        StoreEntity::class,
        RegisterEntity::class,
        TableEntity::class,
        PaymentEntity::class,
        ShiftEntity::class,
        SyncQueueEntity::class,
        ReservationEntity::class,
        GiftCardEntity::class,
        GiftCardTransactionEntity::class,
        AuditLogEntity::class,
        PromotionEntity::class,
        LoyaltyRewardEntity::class,
        ZReportEntity::class,
        InventoryAdjustmentEntity::class,
        InventoryTransferEntity::class,
        ReturnEntity::class,
        MigrationJobEntity::class,
        SettingEntity::class,
        TipLogEntity::class,
        SyncOutboxEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun orderDao(): OrderDao
    abstract fun customerDao(): CustomerDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun storeDao(): StoreDao
    abstract fun tableDao(): TableDao
    abstract fun paymentDao(): PaymentDao
    abstract fun shiftDao(): ShiftDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun reservationDao(): ReservationDao
    abstract fun giftCardDao(): GiftCardDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun promotionDao(): PromotionDao
    abstract fun loyaltyRewardDao(): LoyaltyRewardDao
    abstract fun zReportDao(): ZReportDao
    abstract fun inventoryAdjustmentDao(): InventoryAdjustmentDao
    abstract fun inventoryTransferDao(): InventoryTransferDao
    abstract fun returnDao(): ReturnDao
    abstract fun migrationJobDao(): MigrationJobDao
    abstract fun settingDao(): SettingDao
    abstract fun tipLogDao(): TipLogDao
    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        const val DB_NAME = "enterprise-pos.db"
    }
}
