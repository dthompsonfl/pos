package com.enterprise.pos.data.di

import android.content.Context
import androidx.room.Room
import com.enterprise.pos.core.Clock
import com.enterprise.pos.core.DefaultDispatcherProvider
import com.enterprise.pos.core.DispatcherProvider
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.data.db.PosDatabase
import com.enterprise.pos.data.db.PosMigrations
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
import com.enterprise.pos.data.repository.AnalyticsRepositoryImpl
import com.enterprise.pos.core.security.BiometricAuth
import com.enterprise.pos.core.security.EncryptionManager
import com.enterprise.pos.core.security.SecureStorage
import com.enterprise.pos.data.security.AuditLogRepositoryImpl
import com.enterprise.pos.data.security.SecurityInterceptor
import com.enterprise.pos.data.security.SecurityRepositoryImpl
import com.enterprise.pos.domain.security.AuditLogger
import com.enterprise.pos.domain.security.PermissionChecker
import com.enterprise.pos.domain.security.SessionManager
import com.enterprise.pos.data.repository.CatalogRepositoryImpl
import com.enterprise.pos.data.repository.CustomerRepositoryImpl
import com.enterprise.pos.data.repository.EmployeeRepositoryImpl
import com.enterprise.pos.data.repository.GiftCardRepositoryImpl
import com.enterprise.pos.data.repository.InventoryManagementRepositoryImpl
import com.enterprise.pos.data.repository.MigrationRepositoryImpl
import com.enterprise.pos.data.repository.OrderRepositoryImpl
import com.enterprise.pos.data.repository.PromotionRepositoryImpl
import com.enterprise.pos.data.repository.ReservationRepositoryImpl
import com.enterprise.pos.data.repository.ReturnsRepositoryImpl
import com.enterprise.pos.data.repository.SettingRepositoryImpl
import com.enterprise.pos.data.repository.ShiftRepositoryImpl
import com.enterprise.pos.data.repository.StoreRepositoryImpl
import com.enterprise.pos.data.sync.SyncBackend
import com.enterprise.pos.data.sync.SyncEngine
import com.enterprise.pos.domain.repository.AnalyticsRepository
import com.enterprise.pos.domain.repository.AuditLogRepository
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.CustomerRepository
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.GiftCardRepository
import com.enterprise.pos.domain.repository.InventoryManagementRepository
import com.enterprise.pos.domain.repository.MigrationRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.repository.PromotionRepository
import com.enterprise.pos.domain.repository.ReservationRepository
import com.enterprise.pos.domain.repository.ReturnsRepository
import com.enterprise.pos.domain.repository.ShiftRepository
import com.enterprise.pos.domain.repository.StoreRepository
import com.enterprise.pos.domain.service.CartEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PosDatabase =
        Room.databaseBuilder(ctx, PosDatabase::class.java, PosDatabase.DB_NAME)
            // No destructive migration in production. Add explicit migrations as schema evolves.
            // For dev convenience only, we allow destructive on debug builds via a BuildConfig flag
            // wired by the app module; the data module stays conservative.
            .addMigrations(*PosMigrations.ALL)
            .build()

    @Provides fun provideCatalogDao(db: PosDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideOrderDao(db: PosDatabase): OrderDao = db.orderDao()
    @Provides fun provideCustomerDao(db: PosDatabase): CustomerDao = db.customerDao()
    @Provides fun provideEmployeeDao(db: PosDatabase): EmployeeDao = db.employeeDao()
    @Provides fun provideStoreDao(db: PosDatabase): StoreDao = db.storeDao()
    @Provides fun provideTableDao(db: PosDatabase): TableDao = db.tableDao()
    @Provides fun providePaymentDao(db: PosDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideShiftDao(db: PosDatabase): ShiftDao = db.shiftDao()
    @Provides fun provideSyncQueueDao(db: PosDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideReservationDao(db: PosDatabase): ReservationDao = db.reservationDao()
    @Provides fun provideGiftCardDao(db: PosDatabase): GiftCardDao = db.giftCardDao()
    @Provides fun provideAuditLogDao(db: PosDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun providePromotionDao(db: PosDatabase): PromotionDao = db.promotionDao()
    @Provides fun provideLoyaltyRewardDao(db: PosDatabase): LoyaltyRewardDao = db.loyaltyRewardDao()
    @Provides fun provideZReportDao(db: PosDatabase): ZReportDao = db.zReportDao()
    @Provides fun provideInventoryAdjustmentDao(db: PosDatabase): InventoryAdjustmentDao = db.inventoryAdjustmentDao()
    @Provides fun provideInventoryTransferDao(db: PosDatabase): InventoryTransferDao = db.inventoryTransferDao()
    @Provides fun provideReturnDao(db: PosDatabase): ReturnDao = db.returnDao()
    @Provides fun provideMigrationJobDao(db: PosDatabase): MigrationJobDao = db.migrationJobDao()
    @Provides fun provideSettingDao(db: PosDatabase): SettingDao = db.settingDao()
    @Provides fun provideTipLogDao(db: PosDatabase): TipLogDao = db.tipLogDao()

    @Provides @Singleton
    fun provideClock(): Clock = SystemClock

    @Provides @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides @Singleton
    fun provideLogger(): Logger = NoopLogger

    @Provides @Singleton
    fun provideCartEngine(): CartEngine = CartEngine()

    // --- Base repositories ---
    @Provides @Singleton
    fun provideCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository = impl

    @Provides @Singleton
    fun provideOrderRepository(impl: OrderRepositoryImpl): OrderRepository = impl

    @Provides @Singleton
    fun provideCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository = impl

    @Provides @Singleton
    fun provideEmployeeRepository(impl: EmployeeRepositoryImpl): EmployeeRepository = impl

    @Provides @Singleton
    fun provideStoreRepository(impl: StoreRepositoryImpl): StoreRepository = impl

    @Provides @Singleton
    fun provideSettingsRepository(impl: SettingRepositoryImpl): com.enterprise.pos.domain.repository.SettingsRepository = impl

    @Provides @Singleton
    fun provideCatalogImpl(dao: CatalogDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock): CatalogRepositoryImpl =
        CatalogRepositoryImpl(dao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideOrderImpl(
        dao: OrderDao,
        tableDao: TableDao,
        paymentDao: com.enterprise.pos.data.db.dao.PaymentDao,
        syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao,
        auditLog: com.enterprise.pos.domain.repository.AuditLogRepository,
        cart: CartEngine,
        clock: Clock
    ): OrderRepositoryImpl = OrderRepositoryImpl(dao, tableDao, paymentDao, syncOutboxDao, auditLog, cart, clock)

    @Provides @Singleton
    fun provideCustomerImpl(dao: CustomerDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock): CustomerRepositoryImpl =
        CustomerRepositoryImpl(dao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideEmployeeImpl(dao: EmployeeDao, clock: Clock): EmployeeRepositoryImpl =
        EmployeeRepositoryImpl(dao, clock)

    @Provides @Singleton
    fun provideStoreImpl(dao: StoreDao): StoreRepositoryImpl = StoreRepositoryImpl(dao)

    // --- Enterprise repositories ---
    @Provides @Singleton
    fun provideReservationRepository(impl: ReservationRepositoryImpl): ReservationRepository = impl

    @Provides @Singleton
    fun provideReservationImpl(dao: ReservationDao, tableDao: TableDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock): ReservationRepositoryImpl =
        ReservationRepositoryImpl(dao, tableDao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideGiftCardRepository(impl: GiftCardRepositoryImpl): GiftCardRepository = impl

    @Provides @Singleton
    fun provideGiftCardImpl(dao: GiftCardDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock): GiftCardRepositoryImpl =
        GiftCardRepositoryImpl(dao, syncOutboxDao, clock)

    @Provides @Singleton
    fun providePromotionRepository(impl: PromotionRepositoryImpl): PromotionRepository = impl

    @Provides @Singleton
    fun providePromotionImpl(promoDao: PromotionDao, loyaltyDao: LoyaltyRewardDao, clock: Clock): PromotionRepositoryImpl =
        PromotionRepositoryImpl(promoDao, loyaltyDao, clock)

    @Provides @Singleton
    fun provideShiftRepository(impl: ShiftRepositoryImpl): ShiftRepository = impl

    @Provides @Singleton
    fun provideShiftImpl(
        shiftDao: ShiftDao, orderDao: OrderDao, paymentDao: PaymentDao,
        tipLogDao: TipLogDao, zReportDao: ZReportDao, auditDao: AuditLogDao,
        syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock
    ): ShiftRepositoryImpl = ShiftRepositoryImpl(shiftDao, orderDao, paymentDao, tipLogDao, zReportDao, auditDao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideInventoryManagementRepository(impl: InventoryManagementRepositoryImpl): InventoryManagementRepository = impl

    @Provides @Singleton
    fun provideInventoryMgmtImpl(
        catalogDao: CatalogDao, adjustmentDao: InventoryAdjustmentDao,
        transferDao: InventoryTransferDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock
    ): InventoryManagementRepositoryImpl = InventoryManagementRepositoryImpl(catalogDao, adjustmentDao, transferDao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideReturnsRepository(impl: ReturnsRepositoryImpl): ReturnsRepository = impl

    @Provides @Singleton
    fun provideReturnsImpl(
        returnDao: ReturnDao, orderDao: OrderDao, paymentDao: PaymentDao,
        auditDao: AuditLogDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock
    ): ReturnsRepositoryImpl = ReturnsRepositoryImpl(returnDao, orderDao, paymentDao, auditDao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideAuditLogRepository(impl: AuditLogRepositoryImpl): AuditLogRepository = impl

    @Provides @Singleton
    fun provideAuditLogImpl(dao: AuditLogDao, clock: Clock): AuditLogRepositoryImpl =
        AuditLogRepositoryImpl(dao, clock)

    @Provides @Singleton
    fun provideAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository = impl

    @Provides @Singleton
    fun provideAnalyticsImpl(
        orderDao: OrderDao, catalogDao: CatalogDao, employeeDao: EmployeeDao,
        paymentDao: PaymentDao, syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao, clock: Clock
    ): AnalyticsRepositoryImpl = AnalyticsRepositoryImpl(orderDao, catalogDao, employeeDao, paymentDao, syncOutboxDao, clock)

    @Provides @Singleton
    fun provideMigrationRepository(impl: MigrationRepositoryImpl): MigrationRepository = impl

    @Provides @Singleton
    fun provideMigrationImpl(
        dao: MigrationJobDao, catalogDao: CatalogDao, customerDao: CustomerDao,
        auditDao: AuditLogDao, clock: Clock
    ): MigrationRepositoryImpl = MigrationRepositoryImpl(dao, catalogDao, customerDao, auditDao, clock)

    @Provides @Singleton
    fun provideSettingRepository(dao: SettingDao, clock: Clock): SettingRepositoryImpl =
        SettingRepositoryImpl(dao, clock)

    @Provides @Singleton
    fun provideSyncOutboxDao(db: PosDatabase): com.enterprise.pos.data.sync.SyncOutboxDao =
        db.syncOutboxDao()

    @Provides @Singleton
    fun provideSyncEngine(
        outboxDao: com.enterprise.pos.data.sync.SyncOutboxDao,
        logger: Logger,
        backend: SyncBackend
    ): SyncEngine = SyncEngine(outboxDao, logger, backend)

    // --- Security providers ---
    @Provides @Singleton
    fun providePermissionChecker(): PermissionChecker = PermissionChecker()

    @Provides @Singleton
    fun provideSessionManager(): SessionManager = SessionManager()

    @Provides @Singleton
    fun provideAuditLogger(auditLogRepository: AuditLogRepository): AuditLogger =
        AuditLogger(auditLogRepository)

    @Provides @Singleton
    fun provideSecurityRepository(permissionChecker: PermissionChecker): SecurityRepositoryImpl =
        SecurityRepositoryImpl(permissionChecker)

    @Provides @Singleton
    fun provideSecurityInterceptor(
        permissionChecker: PermissionChecker,
        auditLogger: AuditLogger
    ): SecurityInterceptor = SecurityInterceptor(permissionChecker, auditLogger)

    @Provides @Singleton
    fun provideSecureStorage(@ApplicationContext ctx: Context): SecureStorage =
        SecureStorage(ctx)

    @Provides @Singleton
    fun provideBiometricAuth(@ApplicationContext ctx: Context): BiometricAuth =
        BiometricAuth(ctx)

    @Provides @Singleton
    fun provideEncryptionManager(@ApplicationContext ctx: Context): EncryptionManager =
        EncryptionManager(ctx)
}
