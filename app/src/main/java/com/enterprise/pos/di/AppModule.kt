package com.enterprise.pos.di

import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.Clock
import com.enterprise.pos.domain.service.CartEngine
import com.enterprise.pos.domain.service.DefaultTaxEngine
import com.enterprise.pos.domain.service.TaxConfiguration
import com.enterprise.pos.domain.service.TaxEngine
import com.enterprise.pos.domain.usecase.AddItemToOrderUseCase
import com.enterprise.pos.domain.usecase.ApplyDiscountUseCase
import com.enterprise.pos.domain.usecase.CloseOrderUseCase
import com.enterprise.pos.domain.usecase.CreateOrderUseCase
import com.enterprise.pos.domain.usecase.FinalizeOrderForPaymentUseCase
import com.enterprise.pos.domain.usecase.GetOrderTotalsUseCase
import com.enterprise.pos.domain.usecase.LoginUseCase
import com.enterprise.pos.domain.usecase.ScanProductUseCase
import com.enterprise.pos.domain.usecase.SearchProductsUseCase
import com.enterprise.pos.domain.usecase.SeatTableUseCase
import com.enterprise.pos.domain.usecase.SendToKitchenUseCase
import com.enterprise.pos.domain.usecase.StartTakeoutOrderUseCase
import com.enterprise.pos.domain.usecase.VoidOrderUseCase
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.CustomerRepository
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.data.sync.HttpSyncBackend
import com.enterprise.pos.data.sync.NoopSyncBackend
import com.enterprise.pos.data.sync.SyncBackend
import com.enterprise.pos.payment.adapter.CashPaymentProvider
import com.enterprise.pos.payment.adapter.ManualCardPaymentProvider
import com.enterprise.pos.payment.model.DefaultRoutingPolicy
import com.enterprise.pos.payment.model.PaymentProvider
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentRouterConfig
import com.enterprise.pos.payment.model.PaymentRoutingPolicy
import com.enterprise.pos.payment.router.PaymentRouter
import com.enterprise.pos.payment.shopify.ShopifyPaymentProvider
import com.enterprise.pos.payment.square.SquarePaymentProvider
import com.enterprise.pos.payment.stripe.StripePaymentProvider
import com.enterprise.pos.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideTaxEngine(): TaxEngine = DefaultTaxEngine(TaxConfiguration.RESTAURANT)

    @Provides @Singleton
    fun providePaymentProviders(): Map<PaymentProviderId, @JvmSuppressWildcards PaymentProvider> {
        val providers = mutableMapOf<PaymentProviderId, PaymentProvider>()

        // Stripe: real backend-driven flow. Simulated mode only in debug builds.
        providers[PaymentProviderId.STRIPE] = StripePaymentProvider(
            backendBaseUrl = com.enterprise.pos.BuildConfig.BACKEND_BASE_URL,
            authTokenProvider = { null },
            simulate = com.enterprise.pos.BuildConfig.ENABLE_SIMULATED_PROVIDERS
        )
        // Square / Shopify adapters are simulated in this archive, so they are debug-only.
        if (com.enterprise.pos.BuildConfig.ENABLE_SIMULATED_PROVIDERS) {
            providers[PaymentProviderId.SQUARE] = SquarePaymentProvider()
            providers[PaymentProviderId.SHOPIFY] = ShopifyPaymentProvider()
        }
        // Cash: always real — no SDK required, just accounting
        providers[PaymentProviderId.CASH] = CashPaymentProvider()

        // Manual card entry: PCI risk — only available in debug builds.
        // Release builds do not register this provider so it cannot be used.
        if (com.enterprise.pos.BuildConfig.ENABLE_MANUAL_CARD_ENTRY) {
            providers[PaymentProviderId.MANUAL] = ManualCardPaymentProvider()
        }

        return providers
    }

    @Provides @Singleton
    fun provideRouterConfig(): PaymentRouterConfig {
        val enabled = mutableSetOf(
            PaymentProviderId.STRIPE,
            PaymentProviderId.CASH
        )
        if (com.enterprise.pos.BuildConfig.ENABLE_SIMULATED_PROVIDERS) {
            enabled.add(PaymentProviderId.SQUARE)
            enabled.add(PaymentProviderId.SHOPIFY)
        }
        if (com.enterprise.pos.BuildConfig.ENABLE_MANUAL_CARD_ENTRY) {
            enabled.add(PaymentProviderId.MANUAL)
        }
        return PaymentRouterConfig(
            enabledProviders = enabled,
            defaultProvider = PaymentProviderId.STRIPE,
            fallbackProvider = PaymentProviderId.CASH
        )
    }

    @Provides @Singleton
    fun provideRoutingPolicy(): PaymentRoutingPolicy = DefaultRoutingPolicy()

    @Provides @Singleton
    fun providePaymentRouter(
        providers: Map<PaymentProviderId, @JvmSuppressWildcards PaymentProvider>,
        policy: PaymentRoutingPolicy,
        config: PaymentRouterConfig
    ): PaymentRouter = PaymentRouter(providers, policy, config)

    @Provides @Singleton
    fun provideSyncBackend(logger: Logger): SyncBackend =
        if (com.enterprise.pos.BuildConfig.ENABLE_SIMULATED_PROVIDERS) {
            NoopSyncBackend
        } else {
            HttpSyncBackend(
                baseUrl = com.enterprise.pos.BuildConfig.BACKEND_BASE_URL,
                authTokenProvider = { null },
                logger = logger
            )
        }

    // --- Enterprise domain services ---
    @Provides @Singleton
    fun providePromotionEngine(promotions: List<com.enterprise.pos.domain.model.Promotion>): com.enterprise.pos.domain.service.PromotionEngine =
        com.enterprise.pos.domain.service.PromotionEngine(promotions)

    @Provides @Singleton
    fun providePromotionsList(promoRepo: com.enterprise.pos.domain.repository.PromotionRepository): List<com.enterprise.pos.domain.model.Promotion> =
        kotlinx.coroutines.runBlocking { promoRepo.activeFor(com.enterprise.pos.core.StoreId("store-demo-001"), System.currentTimeMillis()).getOrNull() ?: emptyList() }

    @Provides @Singleton
    fun provideTipPoolEngine(): com.enterprise.pos.domain.service.TipPoolEngine = com.enterprise.pos.domain.service.TipPoolEngine()

    @Provides @Singleton
    fun provideAbcEngine(): com.enterprise.pos.domain.service.AbcAnalysisEngine = com.enterprise.pos.domain.service.AbcAnalysisEngine()

    @Provides @Singleton
    fun provideSplitTenderEngine(): com.enterprise.pos.domain.service.SplitTenderEngine = com.enterprise.pos.domain.service.SplitTenderEngine()

    @Provides @Singleton
    fun provideDatabaseSeeder(
        storeDao: com.enterprise.pos.data.db.dao.StoreDao,
        catalogDao: com.enterprise.pos.data.db.dao.CatalogDao,
        employeeDao: com.enterprise.pos.data.db.dao.EmployeeDao,
        customerDao: com.enterprise.pos.data.db.dao.CustomerDao,
        tableDao: com.enterprise.pos.data.db.dao.TableDao,
        giftCardDao: com.enterprise.pos.data.db.dao.GiftCardDao,
        promotionDao: com.enterprise.pos.data.db.dao.PromotionDao,
        reservationDao: com.enterprise.pos.data.db.dao.ReservationDao
    ): com.enterprise.pos.seed.DatabaseSeeder = com.enterprise.pos.seed.DatabaseSeeder(
        storeDao, catalogDao, employeeDao, customerDao, tableDao, giftCardDao, promotionDao, reservationDao
    )

    // --- Use cases ---
    @Provides fun provideCreateOrder(orders: OrderRepository, clock: Clock) = CreateOrderUseCase(orders, clock)
    @Provides fun provideAddItem(orders: OrderRepository, catalog: CatalogRepository, cart: CartEngine) =
        AddItemToOrderUseCase(orders, catalog, cart)
    @Provides fun provideSendToKitchen(orders: OrderRepository, cart: CartEngine) = SendToKitchenUseCase(orders, cart)
    @Provides fun provideFinalize(orders: OrderRepository, tax: TaxEngine, cart: CartEngine) =
        FinalizeOrderForPaymentUseCase(orders, tax, cart)
    @Provides fun provideCloseOrder(orders: OrderRepository) = CloseOrderUseCase(orders)
    @Provides fun provideVoidOrder(orders: OrderRepository, cart: CartEngine) = VoidOrderUseCase(orders, cart)
    @Provides fun provideApplyDiscount(orders: OrderRepository, cart: CartEngine, employees: EmployeeRepository) =
        ApplyDiscountUseCase(orders, cart, employees)
    @Provides fun provideLogin(employees: EmployeeRepository) = LoginUseCase(employees)
    @Provides fun provideSearch(catalog: CatalogRepository) = SearchProductsUseCase(catalog)
    @Provides fun provideScan(catalog: CatalogRepository) = ScanProductUseCase(catalog)
    @Provides fun provideSeatTable(orders: OrderRepository, clock: Clock) = SeatTableUseCase(orders, clock)
    @Provides fun provideStartTakeout(orders: OrderRepository) = StartTakeoutOrderUseCase(orders)
    @Provides fun provideGetTotals(tax: TaxEngine) = GetOrderTotalsUseCase(tax)
}
