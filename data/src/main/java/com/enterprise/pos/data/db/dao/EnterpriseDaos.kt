package com.enterprise.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations WHERE storeId = :storeId AND requestedAt >= :dayStart AND requestedAt < :dayEnd ORDER BY requestedAt ASC")
    fun observeForDay(storeId: String, dayStart: Long, dayEnd: Long): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE storeId = :storeId AND requestedAt >= :from AND status IN ('REQUESTED','CONFIRMED') ORDER BY requestedAt ASC LIMIT :limit")
    fun observeUpcoming(storeId: String, from: Long, limit: Int): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE id = :id")
    suspend fun get(id: String): ReservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ReservationEntity)

    @Query("UPDATE reservations SET status = :status, confirmedAt = :confirmedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, confirmedAt: Long?)

    @Query("UPDATE reservations SET status = 'SEATED', seatedAt = :seatedAt, tableId = :tableId WHERE id = :id")
    suspend fun seat(id: String, tableId: String, seatedAt: Long)

    @Query("UPDATE reservations SET status = 'CANCELLED', cancelledAt = :cancelledAt, notes = :notes WHERE id = :id")
    suspend fun cancel(id: String, cancelledAt: Long, notes: String)
}

@Dao
interface GiftCardDao {
    @Query("SELECT * FROM gift_cards WHERE code = :code")
    fun observeByCode(code: String): Flow<GiftCardEntity?>

    @Query("SELECT * FROM gift_cards WHERE id = :id")
    suspend fun get(id: String): GiftCardEntity?

    @Query("SELECT * FROM gift_cards WHERE code = :code")
    suspend fun getByCode(code: String): GiftCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: GiftCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logTransaction(tx: GiftCardTransactionEntity)

    @Query("SELECT * FROM gift_card_transactions WHERE giftCardId = :id ORDER BY timestamp DESC")
    suspend fun transactions(id: String): List<GiftCardTransactionEntity>

    @Transaction
    suspend fun adjustBalance(id: String, deltaMinor: Long, tx: GiftCardTransactionEntity): GiftCardEntity {
        val current = get(id)!!
        val newBalance = current.balanceMinor + deltaMinor
        val updated = current.copy(balanceMinor = newBalance.coerceAtLeast(0))
        upsert(updated)
        logTransaction(tx.copy(balanceAfterMinor = newBalance))
        return updated
    }
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log WHERE storeId = :storeId AND (:action IS NULL OR action = :action) ORDER BY timestamp DESC LIMIT :limit")
    fun observe(storeId: String, action: String?, limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    fun observeForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE storeId = :storeId AND timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun range(storeId: String, from: Long, to: Long): List<AuditLogEntity>
}

@Dao
interface PromotionDao {
    @Query("SELECT * FROM promotions WHERE active = 1 ORDER BY priority DESC")
    fun observeActive(): Flow<List<PromotionEntity>>

    @Query("SELECT * FROM promotions ORDER BY priority DESC")
    fun observeAll(): Flow<List<PromotionEntity>>

    @Query("SELECT * FROM promotions WHERE active = 1 AND requiresCode = 1 AND code = :code LIMIT 1")
    suspend fun byCode(code: String): PromotionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PromotionEntity)

    @Query("DELETE FROM promotions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE promotions SET redemptionCount = redemptionCount + 1 WHERE id = :id")
    suspend fun recordRedemption(id: String)
}

@Dao
interface LoyaltyRewardDao {
    @Query("SELECT * FROM loyalty_rewards WHERE active = 1")
    fun observeActive(): Flow<List<LoyaltyRewardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: LoyaltyRewardEntity)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE storeId = :storeId AND status = 'OPEN' ORDER BY startedAt DESC")
    fun observeOpen(storeId: String): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun get(id: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE registerId = :registerId AND status = 'OPEN' LIMIT 1")
    suspend fun openForRegister(registerId: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE employeeId = :employeeId AND status = 'OPEN' LIMIT 1")
    suspend fun openForEmployee(employeeId: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: ShiftEntity)

    @Query("UPDATE shifts SET status = 'CLOSED', endedAt = :endedAt, countedCashMinor = :counted, cashVarianceMinor = :variance, notes = :notes WHERE id = :id")
    suspend fun close(id: String, endedAt: Long, counted: Long, variance: Long, notes: String?)
}

@Dao
interface ZReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ZReportEntity)

    @Query("SELECT * FROM z_reports WHERE storeId = :storeId ORDER BY generatedAt DESC LIMIT :limit")
    suspend fun recent(storeId: String, limit: Int): List<ZReportEntity>
}

@Dao
interface InventoryAdjustmentDao {
    @Query("SELECT * FROM inventory_adjustments WHERE storeId = :storeId AND (:variantId IS NULL OR variantId = :variantId) ORDER BY timestamp DESC LIMIT 200")
    fun observe(storeId: String, variantId: String?): Flow<List<InventoryAdjustmentEntity>>

    @Insert
    suspend fun insert(a: InventoryAdjustmentEntity)
}

@Dao
interface InventoryTransferDao {
    @Query("SELECT * FROM inventory_transfers WHERE fromStoreId = :storeId OR toStoreId = :storeId ORDER BY createdAt DESC")
    fun observe(storeId: String): Flow<List<InventoryTransferEntity>>

    @Query("SELECT * FROM inventory_transfers WHERE id = :id")
    suspend fun get(id: String): InventoryTransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: InventoryTransferEntity)

    @Query("UPDATE inventory_transfers SET status = :status, shippedAt = :shippedAt WHERE id = :id")
    suspend fun ship(id: String, status: String, shippedAt: Long)

    @Query("UPDATE inventory_transfers SET status = :status, receivedAt = :receivedAt, itemsJson = :itemsJson WHERE id = :id")
    suspend fun receive(id: String, status: String, receivedAt: Long, itemsJson: String)
}

@Dao
interface ReturnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ReturnEntity)

    @Query("SELECT * FROM returns WHERE id = :id")
    suspend fun get(id: String): ReturnEntity?

    @Query("SELECT * FROM returns WHERE storeId = :storeId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun history(storeId: String, limit: Int): List<ReturnEntity>
}

@Dao
interface MigrationJobDao {
    @Query("SELECT * FROM migration_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MigrationJobEntity>>

    @Query("SELECT * FROM migration_jobs WHERE id = :id")
    suspend fun get(id: String): MigrationJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(j: MigrationJobEntity)

    @Query("UPDATE migration_jobs SET status = :status, processedRecords = :processed, failedRecords = :failed, errorMessage = :err WHERE id = :id")
    suspend fun progress(id: String, status: String, processed: Int, failed: Int, err: String?)

    @Query("UPDATE migration_jobs SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun complete(id: String, status: String, completedAt: Long)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SettingEntity)
}

@Dao
interface TipLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TipLogEntity)

    @Query("SELECT * FROM tips_log WHERE orderId = :orderId")
    suspend fun forOrder(orderId: String): List<TipLogEntity>

    @Query("SELECT * FROM tips_log WHERE employeeId = :employeeId AND timestamp >= :from AND timestamp <= :to")
    suspend fun forEmployee(employeeId: String, from: Long, to: Long): List<TipLogEntity>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM tips_log WHERE timestamp >= :from AND timestamp <= :to")
    suspend fun sumBetween(from: Long, to: Long): Long
}
