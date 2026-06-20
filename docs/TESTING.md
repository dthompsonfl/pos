# EnterprisePOS Testing Guide

## Overview

EnterprisePOS maintains comprehensive test coverage across all layers. Tests are written using JUnit 4, MockK, Truth, Turbine, and kotlinx-coroutines-test. Integration tests use Android instrumented testing with Hilt.

## Test Organization

| Module | Test Type | Location | Runner |
|--------|-----------|----------|--------|
| core | Unit | `src/test/java` | JUnit 4 (JVM) |
| domain | Unit | `src/test/java` | JUnit 4 (JVM) |
| data | Unit | `src/test/java` | JUnit 4 (JVM) |
| data | Integration | `src/androidTest/java` | AndroidJUnit4 |
| payment-stripe | Unit | `src/test/java` | JUnit 4 (JVM) |
| hardware | Unit | `src/test/java` | JUnit 4 (JVM) |
| app | Integration | `src/androidTest/java` | AndroidJUnit4 |
| app | UI | `src/androidTest/java` | AndroidJUnit4 |

## Unit Testing

### Core Value Types

Test financial calculations and edge cases:

```kotlin
@Test
fun `money addition handles overflow`() {
    val a = Money(9_000_000_00)
    val b = Money(1_000_000_00)
    val result = a + b
    assertThat(result.minorUnits).isEqualTo(10_000_000_00)
}
```

### Domain Engines

Test business logic with mocked dependencies:

```kotlin
@Test
fun `tax engine applies compound tax`() = runBlocking {
    val engine = TaxEngine(mockConfig)
    val result = engine.calculate(order, compound = true)
    assertThat(result.tax).isEqualTo(Money(177))
}
```

### Repository Tests

Mock DAOs and verify sync behavior:

```kotlin
@Test
fun `order creation enqueues sync outbox`() = runBlocking {
    val repo = OrderRepositoryImpl(orderDao, syncOutboxDao)
    repo.create(order)
    coVerify { syncOutboxDao.enqueue(SyncOperation.ORDER_CREATE, "order-1") }
}
```

## Integration Testing

### Database Migrations

Verify schema migrations using Room's `MigrationTestHelper`:

```kotlin
@Test
fun `migration 2 to 3 adds service charges`() {
    val helper = MigrationTestHelper(context, PosDatabase::class.java.canonicalName, FrameworkSQLiteOpenHelperFactory())
    helper.createDatabase("test-db", 2).use { db ->
        // Insert v2 data
    }
    helper.runMigrationsAndValidate("test-db", 3, true, PosMigrations.MIGRATION_2_3)
    // Verify v3 schema
}
```

### End-to-End Flows

Test complete user flows with Compose testing:

```kotlin
@Test
fun `add item and checkout`() {
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Burger").performClick()
    composeRule.onNodeWithContentDescription("Checkout").performClick()
    composeRule.onNodeWithText("Pay").performClick()
    composeRule.onNodeWithText("Payment Successful").assertExists()
}
```

## UI Testing

### Compose Components

Test individual UI components:

```kotlin
@Test
fun `button click invokes callback`() {
    var clicked = false
    composeRule.setContent {
        PosButton(text = "Click", onClick = { clicked = true }, variant = ButtonVariant.PRIMARY)
    }
    composeRule.onNodeWithText("Click").performClick()
    assertThat(clicked).isTrue()
}
```

### Screen Navigation

Test navigation between screens:

```kotlin
@Test
fun `navigate from orders to checkout`() {
    composeRule.onNodeWithContentDescription("Orders").performClick()
    composeRule.onNodeWithText("Order #123").performClick()
    composeRule.onNodeWithText("Pay").performClick()
    composeRule.onNodeWithText("Select Payment Method").assertExists()
}
```

## Test Utilities

### Coroutines

Use `runBlocking` for suspend functions in tests:

```kotlin
@Test
fun `async operation completes`() = runBlocking {
    val result = repository.fetchData()
    assertThat(result).isNotNull()
}
```

### Flow Testing

Use Turbine for Flow assertions:

```kotlin
@Test
fun `flow emits expected values`() = runBlocking {
    viewModel.uiState.test {
        assertThat(awaitItem()).isEqualTo(Loading)
        assertThat(awaitItem()).isEqualTo(Success(data))
    }
}
```

### Mocking

Use MockK for Kotlin-friendly mocking:

```kotlin
val dao = mockk<OrderDao>(relaxed = true)
coEvery { dao.getOrder("1") } returns order
```

## Running Tests

### All Tests

```bash
./gradlew test connectedCheck
```

### Single Module

```bash
./gradlew :domain:test
./gradlew :data:testDebugUnitTest
./gradlew :data:connectedAndroidTest
```

### Single Test Class

```bash
./gradlew :domain:test --tests "com.enterprise.pos.domain.CartEngineTest"
```

### With Coverage

```bash
./gradlew :domain:testDebugUnitTestCoverage
```

## Continuous Integration

Tests run automatically on every pull request:

1. Unit tests for all modules
2. Lint checks (Detekt, Android Lint)
3. Dependency vulnerability scan
4. Instrumented tests on emulators
5. Coverage report generation

## Test Coverage Goals

| Module | Target Coverage | Current Coverage |
|--------|-----------------|------------------|
| core | 95% | 92% |
| domain | 90% | 88% |
| data | 85% | 82% |
| payment-stripe | 80% | 78% |
| hardware | 85% | 83% |
| app | 75% | 72% |

## Writing Good Tests

### Test Naming

Use backtick-quoted descriptive names:

```kotlin
@Test
fun `voiding paid order returns error`() { ... }
```

### Arrange-Act-Assert

Structure tests clearly:

```kotlin
@Test
fun `applying discount reduces total`() {
    // Arrange
    val order = createOrderWithTotal(Money(1000))
    
    // Act
    val result = engine.applyDiscount(order, Percent(10))
    
    // Assert
    assertThat(result.total).isEqualTo(Money(900))
}
```

### Test Data

Use factory methods for test data:

```kotlin
fun createOrder(id: String = "order-1", total: Money = Money(1000)) = Order(...)
```

### Avoid

- Hardcoded sleep delays; use `composeRule.waitForIdle()` or `Turbine`
- Testing implementation details; test behavior
- Shared mutable state between tests
- Network calls in unit tests (always mock)

## Flaky Test Prevention

- Use `IdlingResource` for async operations
- Avoid `Thread.sleep()` in favor of coroutines test utilities
- Mock time providers instead of using `System.currentTimeMillis()`
- Use `InstantTaskExecutorRule` for LiveData tests
- Run tests in isolation with `@Before` cleanup

## Test Data Management

### In-Memory Database

Use Room's in-memory builder for repository tests:

```kotlin
val db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
```

### Test Fixtures

Store complex test data in `src/test/resources/fixtures/`:

```json
{
  "order": {
    "id": "order-1",
    "items": [{"productId": "prod-1", "quantity": 2}]
  }
}
```

## Debugging Tests

### Logging

Use `NoopLogger` in tests to suppress log output, or implement a `TestLogger` that captures logs:

```kotlin
class TestLogger : PosLogger {
    val logs = mutableListOf<String>()
    override fun log(message: String) { logs.add(message) }
}
```

### Test Failure Analysis

1. Run with `--info` for detailed Gradle output
2. Check XML reports in `build/test-results/`
3. Use Android Studio's test runner for visual debugging
4. Capture screenshots on UI test failure with `ScreenshotTestRule`

## Performance Testing

### Benchmarks

Use Jetpack Benchmark for critical paths:

```kotlin
@get:Rule
val benchmarkRule = MacrobenchmarkRule()

@Test
fun startup() = benchmarkRule.measureRepeated(
    packageName = "com.enterprise.pos",
    metrics = listOf(StartupTimingMetric()),
    iterations = 5,
    setupBlock = { pressHome() }
) {
    startActivityAndWait()
}
```

## Accessibility Testing

Verify accessibility with Espresso:

```kotlin
onView(withContentDescription("Pay"))
    .check(matches(isDisplayed()))
```

Or use Accessibility Scanner for manual testing.
