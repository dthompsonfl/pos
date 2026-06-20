package com.enterprise.pos.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations — production-safe (no destructive changes).
 *
 * Pattern for adding a new migration:
 *   1. Bump PosDatabase version.
 *   2. Add a `val MIGRATION_N_NPLUS1 = object : Migration(N, N+1) { ... }` here.
 *   3. Add it to the [ALL] array.
 *
 * Each migration must use ALTER TABLE or CREATE TABLE statements — never DROP TABLE
 * in a production migration (data loss). Use `ALTER TABLE x ADD COLUMN y ...` to add
 * nullable or default-valued columns.
 */
object PosMigrations {

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    id TEXT NOT NULL PRIMARY KEY,
                    storeId TEXT NOT NULL,
                    registerId TEXT,
                    employeeId TEXT,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    schemaVersion INTEGER NOT NULL,
                    idempotencyKey TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    nextAttemptAt INTEGER NOT NULL,
                    lastError TEXT,
                    status TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_status ON sync_outbox(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_nextAttemptAt ON sync_outbox(nextAttemptAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_entityType ON sync_outbox(entityType)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_idempotencyKey ON sync_outbox(idempotencyKey)")
            db.execSQL("ALTER TABLE orders ADD COLUMN serviceChargesMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE orders ADD COLUMN taxExempt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE order_lines ADD COLUMN taxCategory TEXT NOT NULL DEFAULT 'STANDARD'")
            db.execSQL("ALTER TABLE order_lines ADD COLUMN taxAmountMinor INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add extended customer fields
            db.execSQL("ALTER TABLE customers ADD COLUMN firstName TEXT")
            db.execSQL("ALTER TABLE customers ADD COLUMN lastName TEXT")
            db.execSQL("ALTER TABLE customers ADD COLUMN city TEXT")
            db.execSQL("ALTER TABLE customers ADD COLUMN state TEXT")
            db.execSQL("ALTER TABLE customers ADD COLUMN zip TEXT")
            db.execSQL("ALTER TABLE customers ADD COLUMN country TEXT NOT NULL DEFAULT 'USA'")
            db.execSQL("ALTER TABLE customers ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE customers ADD COLUMN customerGroup TEXT")
            db.execSQL("ALTER TABLE customers ADD COLUMN loyaltyNumber TEXT")

            // Add extended employee fields
            db.execSQL("ALTER TABLE employees ADD COLUMN firstName TEXT")
            db.execSQL("ALTER TABLE employees ADD COLUMN lastName TEXT")
            db.execSQL("ALTER TABLE employees ADD COLUMN hourlyRateMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE employees ADD COLUMN hireDate INTEGER")
            db.execSQL("ALTER TABLE employees ADD COLUMN notes TEXT")
            db.execSQL("ALTER TABLE employees ADD COLUMN customPermissions TEXT NOT NULL DEFAULT ''")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5
    )
}
