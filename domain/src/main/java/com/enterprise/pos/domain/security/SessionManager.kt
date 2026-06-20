package com.enterprise.pos.domain.security

import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.domain.model.EmployeeRole
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Session represents an authenticated employee session on a specific device.
 */
data class Session(
    val sessionId: String,
    val employeeId: EmployeeId,
    val employeeName: String,
    val deviceId: String,
    val role: EmployeeRole,
    val startedAt: Long,
    var lastActivityAt: Long,
    val permissions: List<String> = emptyList()
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val expiresAt: Long get() = lastActivityAt + sessionTimeoutMs(role)
    val remainingMs: Long get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
}

/**
 * SessionManager manages employee sessions with role-based timeouts,
 * concurrency limits, and automatic invalidation.
 *
 * PCI DSS Level 4: Sessions must timeout after a period of inactivity (Req 8.1.8).
 * Admin sessions may have extended timeouts for overnight operations.
 *
 * FIPS 140-2: Session tokens are ephemeral; long-term secrets are never stored in memory.
 */
class SessionManager {

    private val sessions = ConcurrentHashMap<String, Session>()
    private val employeeSessions = ConcurrentHashMap<String, MutableSet<String>>()

    companion object {
        private const val MAX_CONCURRENT_SESSIONS = 2

        fun sessionTimeoutMs(role: EmployeeRole): Long = when (role) {
            EmployeeRole.CASHIER -> 15 * 60 * 1000L // 15 minutes
            EmployeeRole.SERVER -> 15 * 60 * 1000L
            EmployeeRole.HOST -> 15 * 60 * 1000L
            EmployeeRole.BARTENDER -> 20 * 60 * 1000L // 20 minutes
            EmployeeRole.LINE_COOK -> 15 * 60 * 1000L
            EmployeeRole.KITCHEN_LEAD -> 30 * 60 * 1000L // 30 minutes
            EmployeeRole.SHIFT_LEAD -> 30 * 60 * 1000L
            EmployeeRole.MANAGER -> 30 * 60 * 1000L // 30 minutes
            EmployeeRole.ADMIN -> 8 * 60 * 60 * 1000L // 8 hours
        }
    }

    /**
     * Start a new session for an employee. If the employee already has
     * the maximum number of concurrent sessions, the oldest one is invalidated.
     */
    fun startSession(
        employeeId: EmployeeId,
        employeeName: String,
        role: EmployeeRole,
        deviceId: String,
        permissions: List<String> = emptyList()
    ): Session {
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()

        // Enforce max concurrent sessions
        val existing = employeeSessions.getOrPut(employeeId.value) { ConcurrentHashMap.newKeySet() }
        synchronized(existing) {
            while (existing.size >= MAX_CONCURRENT_SESSIONS) {
                val oldest = existing.minByOrNull { sid -> sessions[sid]?.startedAt ?: Long.MAX_VALUE }
                if (oldest != null) {
                    endSession(oldest)
                } else {
                    break
                }
            }
            existing.add(sessionId)
        }

        val session = Session(
            sessionId = sessionId,
            employeeId = employeeId,
            employeeName = employeeName,
            deviceId = deviceId,
            role = role,
            startedAt = now,
            lastActivityAt = now,
            permissions = permissions
        )
        sessions[sessionId] = session
        return session
    }

    /**
     * End a session and clean up associated state.
     */
    fun endSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        employeeSessions[session.employeeId.value]?.let { set ->
            synchronized(set) { set.remove(sessionId) }
            if (set.isEmpty()) {
                employeeSessions.remove(session.employeeId.value)
            }
        }
    }

    /**
     * Get the current session by ID, or null if not found or expired.
     */
    fun getSession(sessionId: String): Session? {
        val session = sessions[sessionId] ?: return null
        if (session.isExpired) {
            endSession(sessionId)
            return null
        }
        return session
    }

    /**
     * Get the current active session, requiring it to be valid.
     */
    fun getCurrentSession(): Session? {
        return sessions.values.firstOrNull { !it.isExpired }
    }

    /**
     * Check if a session is valid (exists and not expired).
     */
    fun isSessionValid(sessionId: String): Boolean {
        return getSession(sessionId) != null
    }

    /**
     * Require a valid session; throws SecurityException if none exists or expired.
     */
    fun requireValidSession(sessionId: String): Session {
        return getSession(sessionId)
            ?: throw SecurityException("Session is invalid or expired. Please log in again.")
    }

    /**
     * Refresh a session by extending its TTL from the current time.
     */
    fun refreshSession(sessionId: String): Session? {
        val session = getSession(sessionId) ?: return null
        val refreshed = session.copy(lastActivityAt = System.currentTimeMillis())
        sessions[sessionId] = refreshed
        return refreshed
    }

    /**
     * Invalidate all sessions for an employee. Used when permissions change
     * or the employee is deactivated.
     */
    fun invalidateEmployeeSessions(employeeId: EmployeeId) {
        val sessionIds = employeeSessions[employeeId.value]?.toSet() ?: return
        for (sid in sessionIds) {
            endSession(sid)
        }
    }

    /**
     * Clean up all expired sessions.
     */
    fun cleanupExpiredSessions() {
        val expired = sessions.filterValues { it.isExpired }.keys
        for (sid in expired) {
            endSession(sid)
        }
    }

    /**
     * Get the number of active sessions for an employee.
     */
    fun activeSessionCount(employeeId: EmployeeId): Int {
        cleanupExpiredSessions()
        return employeeSessions[employeeId.value]?.let { set ->
            synchronized(set) { set.size }
        } ?: 0
    }

    /**
     * Get all active sessions.
     */
    fun allActiveSessions(): List<Session> {
        cleanupExpiredSessions()
        return sessions.values.toList()
    }
}
