package com.enterprise.pos.domain.security

import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionCheckerTest {

    @Test
    fun `cashier permissions`() {
        val perms = RolePermissions(
            role = EmployeeRole.CASHIER,
            canApplyDiscounts = true,
            maxDiscountPercent = 5,
            canOpenDrawer = true
        )
        assertThat(perms.canApplyDiscounts).isTrue()
        assertThat(perms.maxDiscountPercent).isEqualTo(5)
        assertThat(perms.canProcessRefunds).isFalse()
        assertThat(perms.canVoidOrders).isFalse()
    }

    @Test
    fun `manager permissions`() {
        val perms = RolePermissions(
            role = EmployeeRole.MANAGER,
            canProcessRefunds = true,
            canApplyDiscounts = true,
            maxDiscountPercent = 100,
            canVoidOrders = true,
            canOpenDrawer = true,
            canManageEmployees = true,
            canViewReports = true,
            canManageInventory = true,
            canCompItems = true,
            maxCompValue = com.enterprise.pos.core.Money.of(500.0)
        )
        assertThat(perms.canManageEmployees).isTrue()
        assertThat(perms.canViewReports).isTrue()
        assertThat(perms.maxDiscountPercent).isEqualTo(100)
    }

    @Test
    fun `admin has all permissions`() {
        val perms = RolePermissions(
            role = EmployeeRole.ADMIN,
            canProcessRefunds = true,
            canApplyDiscounts = true,
            maxDiscountPercent = 100,
            canVoidOrders = true,
            canOpenDrawer = true,
            canManageEmployees = true,
            canViewReports = true,
            canManageInventory = true,
            canCompItems = true,
            maxCompValue = com.enterprise.pos.core.Money.of(10000.0)
        )
        assertThat(perms.canProcessRefunds).isTrue()
        assertThat(perms.canManageEmployees).isTrue()
        assertThat(perms.canViewReports).isTrue()
        assertThat(perms.canManageInventory).isTrue()
    }

    @Test
    fun `line cook has minimal permissions`() {
        val perms = RolePermissions(role = EmployeeRole.LINE_COOK)
        assertThat(perms.canApplyDiscounts).isFalse()
        assertThat(perms.canOpenDrawer).isFalse()
    }

    @Test
    fun `server can comp items up to limit`() {
        val perms = RolePermissions(
            role = EmployeeRole.SERVER,
            canApplyDiscounts = true,
            maxDiscountPercent = 10,
            canCompItems = true,
            maxCompValue = com.enterprise.pos.core.Money.of(25.0)
        )
        assertThat(perms.canCompItems).isTrue()
        assertThat(perms.maxCompValue).isEqualTo(com.enterprise.pos.core.Money.of(25.0))
    }

    @Test
    fun `custom overrides extend base permissions`() {
        val perms = RolePermissions(
            role = EmployeeRole.CASHIER,
            canApplyDiscounts = true,
            maxDiscountPercent = 5,
            canOpenDrawer = true,
            canProcessRefunds = true // custom override
        )
        assertThat(perms.canProcessRefunds).isTrue()
    }

    @Test
    fun `EmployeeSession hasPermission checks active status`() {
        val future = System.currentTimeMillis() + 3600000
        val session = EmployeeSession(
            employeeId = com.enterprise.pos.core.EmployeeId("emp-1"),
            employeeName = "Test",
            role = EmployeeRole.CASHIER,
            storeId = com.enterprise.pos.core.StoreId("store-1"),
            registerId = com.enterprise.pos.core.RegisterId("reg-1"),
            startedAt = System.currentTimeMillis(),
            expiresAt = future,
            permissions = RolePermissions(
                role = EmployeeRole.CASHIER,
                canApplyDiscounts = true
            )
        )
        assertThat(session.hasPermission { canApplyDiscounts }).isTrue()
        assertThat(session.hasPermission { canManageEmployees }).isFalse()
    }

    @Test
    fun `expired session has no permissions`() {
        val past = System.currentTimeMillis() - 1000
        val session = EmployeeSession(
            employeeId = com.enterprise.pos.core.EmployeeId("emp-1"),
            employeeName = "Test",
            role = EmployeeRole.MANAGER,
            storeId = com.enterprise.pos.core.StoreId("store-1"),
            registerId = com.enterprise.pos.core.RegisterId("reg-1"),
            startedAt = past - 3600000,
            expiresAt = past,
            permissions = RolePermissions(
                role = EmployeeRole.MANAGER,
                canManageEmployees = true
            )
        )
        assertThat(session.isActive).isFalse()
        assertThat(session.hasPermission { canManageEmployees }).isFalse()
    }

    @Test
    fun `ManagerOverride isActive checks expiration`() {
        val future = System.currentTimeMillis() + 300000
        val override = ManagerOverride(
            managerId = com.enterprise.pos.core.EmployeeId("mgr-1"),
            managerName = "Manager",
            targetAction = "discount",
            reason = "Customer complaint",
            grantedAt = System.currentTimeMillis(),
            expiresAt = future
        )
        assertThat(override.isActive).isTrue()
    }

    @Test
    fun `expired ManagerOverride is not active`() {
        val past = System.currentTimeMillis() - 1000
        val override = ManagerOverride(
            managerId = com.enterprise.pos.core.EmployeeId("mgr-1"),
            managerName = "Manager",
            targetAction = "discount",
            reason = "Customer complaint",
            grantedAt = past - 300000,
            expiresAt = past
        )
        assertThat(override.isActive).isFalse()
    }
}
