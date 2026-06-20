package com.enterprise.pos.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PosMigrationsTest {

    @Test
    fun allRegistersEveryKnownProductionMigration() {
        val versionPairs = PosMigrations.ALL.map { it.startVersion to it.endVersion }

        assertThat(versionPairs).contains(2 to 3)
    }

    @Test
    fun allDoesNotRegisterDuplicateVersionPairs() {
        val versionPairs = PosMigrations.ALL.map { it.startVersion to it.endVersion }

        assertThat(versionPairs.toSet()).hasSize(versionPairs.size)
    }
}
