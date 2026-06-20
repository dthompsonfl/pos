package com.enterprise.pos.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations for production-safe upgrades.
 *
 * New migrations must be additive or otherwise data-preserving. Do not use
 * fallbackToDestructiveMigration in production builds and do not drop merchant data
 * inside a migration.
 */
object PosMigrations {

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
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
                """.trimIndent()
            )
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

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_2_3
    )
}
