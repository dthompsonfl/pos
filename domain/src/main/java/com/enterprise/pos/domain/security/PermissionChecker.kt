package com.enterprise.pos.domain.security

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions

/**
 * PermissionChecker enforces role-based access control (RBAC) with custom per-employee
 * overrides and manager approval flow for high-risk operations.
 *
 * PCI DSS Level 4: Access control restricts system access to authorized individuals
 * (Req 7.1, 8.1). Role-based permissions ensure least-privilege access.
 *
 * FIPS 140-2: Not directly applicable, but access control complements cryptographic
 * protections by limiting who can perform sensitive operations.
 */
class PermissionChecker {

    /**
     * Action represents the type of operation being requested.
     */
    enum class Action {
        VIEW, CREATE, UPDATE, DELETE, PROCESS, MANAGE, EXPORT
    }

    /**
     * Resource represents the domain entity being acted upon.
     */
    enum class Resource {
        ORDER, PRODUCT, CUSTOMER, EMPLOYEE, INVENTORY, REPORT, SETTING, SHIFT, PAYMENT, REFUND
    }

    /**
     * Permission combines an action, resource, and optional store scope.
     */
    data class Permission(
        val action: Action,
        val resource: Resource,
        val storeId: String? = null
    ) {
        fun asString(): String = "${action.name}_${resource.name}"
    }

    /**
     * Check if an employee has a specific permission string (e.g., "MANAGE_EMPLOYEE").
     */
    fun checkPermission(employee: Employee, permission: String): Boolean {
        val parts = permission.split("_", limit = 2)
        if (parts.size != 2) return false
        val action = runCatching { Action.valueOf(parts[0]) }.getOrNull() ?: return false
        val resource = runCatching { Resource.valueOf(parts[1]) }.getOrNull() ?: return false
        return checkPermission(employee, action, resource)
    }

    /**
     * Check if an employee has permission to perform an action on a resource.
     * Evaluates role-based defaults, then custom per-employee overrides.
     */
    fun checkPermission(employee: Employee, action: Action, resource: Resource): Boolean {
        if (!employee.active) return false

        // Check custom permissions first (positive and negative overrides)
        val customPerm = Permission(action, resource)
        val customString = customPerm.asString()
        if (employee.customPermissions.contains("-$customString")) {
            return false // Explicit denial
        }
        if (employee.customPermissions.contains(customString)) {
            return true // Explicit grant
        }

        // Fall back to role-based permissions
        val rolePerms = rolePermissions(employee.role)
        return hasRolePermission(rolePerms, action, resource)
    }

    /**
     * Require a permission; throws SecurityException if denied.
     */
    fun requirePermission(employee: Employee, permission: String) {
        if (!checkPermission(employee, permission)) {
            throw SecurityException("Permission denied: $permission")
        }
    }

    /**
     * Require a permission; throws SecurityException if denied.
     */
    fun requirePermission(employee: Employee, action: Action, resource: Resource) {
        if (!checkPermission(employee, action, resource)) {
            throw SecurityException("Permission denied: ${action.name}_${resource.name}")
        }
    }

    /**
     * Check if a high-risk operation requires manager approval.
     * Returns true if the operation is allowed (either by role or by override).
     *
     * High-risk operations:
     *  - Discounts exceeding the employee's max discount threshold
     *  - Voids on paid orders
     *  - Refunds above a threshold
     *  - Comps above a threshold
     *  - Employee permission changes
     *  - Settings changes
     *  - Inventory adjustments outside normal hours
     */
    fun requiresManagerApproval(
        employee: Employee,
        action: Action,
        resource: Resource,
        amount: Money? = null,
        discountPercent: Int? = null
    ): Boolean {
        if (!employee.active) return true
        val rolePerms = rolePermissions(employee.role)

        return when {
            // Discounts above role threshold require manager approval
            action == Action.PROCESS && resource == Resource.ORDER && discountPercent != null -> {
                discountPercent > rolePerms.maxDiscountPercent
            }
            // Comps above role threshold require manager approval
            action == Action.PROCESS && resource == Resource.ORDER && amount != null -> {
                rolePerms.maxCompValue > Money.ZERO && amount > rolePerms.maxCompValue
            }
            // Refunds require explicit role permission
            action == Action.PROCESS && resource == Resource.REFUND -> {
                !rolePerms.canProcessRefunds
            }
            // Voids require explicit role permission
            action == Action.DELETE && resource == Resource.ORDER -> {
                !rolePerms.canVoidOrders
            }
            // Employee management requires manager permission
            resource == Resource.EMPLOYEE && action in setOf(Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE) -> {
                !rolePerms.canManageEmployees
            }
            // Settings changes require admin/manager
            resource == Resource.SETTING && action == Action.MANAGE -> {
                !rolePerms.canManageEmployees
            }
            // Inventory adjustments require inventory management permission
            resource == Resource.INVENTORY && action == Action.MANAGE -> {
                !rolePerms.canManageInventory
            }
            // Report export requires report permission
            resource == Resource.REPORT && action == Action.EXPORT -> {
                !rolePerms.canViewReports
            }
            else -> false
        }
    }

    /**
     * Validate a manager override for a high-risk operation.
     * The manager must have the target permission and be active.
     */
    fun validateManagerOverride(
        manager: Employee,
        action: Action,
        resource: Resource
    ): Boolean {
        if (!manager.active) return false
        return checkPermission(manager, action, resource)
    }

    /**
     * Get the default role permissions for a given role.
     */
    fun rolePermissions(role: EmployeeRole): RolePermissions = when (role) {
        EmployeeRole.CASHIER -> RolePermissions(
            role = role,
            canProcessRefunds = false,
            canApplyDiscounts = true,
            maxDiscountPercent = 5,
            canVoidOrders = false,
            canOpenDrawer = true,
            canManageEmployees = false,
            canViewReports = false,
            canManageInventory = false,
            canCompItems = false,
            maxCompValue = Money.ZERO
        )
        EmployeeRole.SERVER -> RolePermissions(
            role = role,
            canProcessRefunds = false,
            canApplyDiscounts = true,
            maxDiscountPercent = 10,
            canVoidOrders = false,
            canOpenDrawer = false,
            canManageEmployees = false,
            canViewReports = false,
            canManageInventory = false,
            canCompItems = true,
            maxCompValue = Money.of(25.0)
        )
        EmployeeRole.HOST -> RolePermissions(
            role = role,
            canProcessRefunds = false,
            canApplyDiscounts = false,
            maxDiscountPercent = 0,
            canVoidOrders = false,
            canOpenDrawer = false,
            canManageEmployees = false,
            canViewReports = false,
            canManageInventory = false,
            canCompItems = false,
            maxCompValue = Money.ZERO
        )
        EmployeeRole.BARTENDER -> RolePermissions(
            role = role,
            canProcessRefunds = false,
            canApplyDiscounts = true,
            maxDiscountPercent = 15,
            canVoidOrders = false,
            canOpenDrawer = true,
            canManageEmployees = false,
            canViewReports = false,
            canManageInventory = false,
            canCompItems = true,
            maxCompValue = Money.of(50.0)
        )
        EmployeeRole.LINE_COOK -> RolePermissions(
            role = role,
            canProcessRefunds = false,
            canApplyDiscounts = false,
            maxDiscountPercent = 0,
            canVoidOrders = false,
            canOpenDrawer = false,
            canManageEmployees = false,
            canViewReports = false,
            canManageInventory = false,
            canCompItems = false,
            maxCompValue = Money.ZERO
        )
        EmployeeRole.KITCHEN_LEAD -> RolePermissions(
            role = role,
            canProcessRefunds = false,
            canApplyDiscounts = true,
            maxDiscountPercent = 25,
            canVoidOrders = false,
            canOpenDrawer = false,
            canManageEmployees = false,
            canViewReports = false,
            canManageInventory = true,
            canCompItems = true,
            maxCompValue = Money.of(25.0)
        )
        EmployeeRole.SHIFT_LEAD -> RolePermissions(
            role = role,
            canProcessRefunds = true,
            canApplyDiscounts = true,
            maxDiscountPercent = 25,
            canVoidOrders = true,
            canOpenDrawer = true,
            canManageEmployees = false,
            canViewReports = true,
            canManageInventory = false,
            canCompItems = true,
            maxCompValue = Money.of(75.0)
        )
        EmployeeRole.MANAGER -> RolePermissions(
            role = role,
            canProcessRefunds = true,
            canApplyDiscounts = true,
            maxDiscountPercent = 100,
            canVoidOrders = true,
            canOpenDrawer = true,
            canManageEmployees = true,
            canViewReports = true,
            canManageInventory = true,
            canCompItems = true,
            maxCompValue = Money.of(500.0)
        )
        EmployeeRole.ADMIN -> RolePermissions(
            role = role,
            canProcessRefunds = true,
            canApplyDiscounts = true,
            maxDiscountPercent = 100,
            canVoidOrders = true,
            canOpenDrawer = true,
            canManageEmployees = true,
            canViewReports = true,
            canManageInventory = true,
            canCompItems = true,
            maxCompValue = Money.of(10_000.0)
        )
    }

    private fun hasRolePermission(rolePerms: RolePermissions, action: Action, resource: Resource): Boolean {
        return when (resource) {
            Resource.ORDER -> when (action) {
                Action.VIEW, Action.CREATE, Action.UPDATE, Action.PROCESS -> true
                Action.DELETE -> rolePerms.canVoidOrders
                Action.MANAGE -> rolePerms.canVoidOrders || rolePerms.canProcessRefunds
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.PRODUCT -> when (action) {
                Action.VIEW -> true
                Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE -> rolePerms.canManageInventory
                Action.PROCESS -> true
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.CUSTOMER -> when (action) {
                Action.VIEW, Action.CREATE, Action.UPDATE -> true
                Action.DELETE -> rolePerms.canManageEmployees
                Action.MANAGE -> rolePerms.canManageEmployees
                Action.PROCESS -> true
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.EMPLOYEE -> when (action) {
                Action.VIEW -> true
                Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE -> rolePerms.canManageEmployees
                Action.PROCESS -> rolePerms.canManageEmployees
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.INVENTORY -> when (action) {
                Action.VIEW -> true
                Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE -> rolePerms.canManageInventory
                Action.PROCESS -> rolePerms.canManageInventory
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.REPORT -> when (action) {
                Action.VIEW, Action.EXPORT -> rolePerms.canViewReports
                Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE, Action.PROCESS -> false
            }
            Resource.SETTING -> when (action) {
                Action.VIEW -> true
                Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE -> rolePerms.canManageEmployees
                Action.PROCESS -> rolePerms.canManageEmployees
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.SHIFT -> when (action) {
                Action.VIEW -> true
                Action.CREATE, Action.UPDATE, Action.PROCESS, Action.MANAGE -> true
                Action.DELETE -> rolePerms.canManageEmployees
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.PAYMENT -> when (action) {
                Action.VIEW, Action.CREATE, Action.PROCESS -> true
                Action.UPDATE, Action.DELETE -> rolePerms.canProcessRefunds
                Action.MANAGE -> rolePerms.canProcessRefunds
                Action.EXPORT -> rolePerms.canViewReports
            }
            Resource.REFUND -> when (action) {
                Action.VIEW, Action.PROCESS -> rolePerms.canProcessRefunds
                Action.CREATE, Action.UPDATE, Action.DELETE, Action.MANAGE -> rolePerms.canProcessRefunds
                Action.EXPORT -> rolePerms.canViewReports
            }
        }
    }
}
