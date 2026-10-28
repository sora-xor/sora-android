/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.core_db.migrations.migration_CardHub_65_66
import jp.co.soramitsu.core_db.migrations.migration_CardHub_66_67
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations 65 -> 66 and 66 -> 67 used to read the live `CardHubType` enum. SORA Card and
 * "Buy XOR" were removed from that enum in 3.9.0, so the migrations now use fixed values.
 * These tests pin the rows they write to what earlier releases wrote, so a user upgrading
 * from a very old database still gets the same `globalCardsHub` table.
 */
@RunWith(AndroidJUnit4::class)
class CardHubMigrationRowsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun removeTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration65To66WritesTheSameGlobalHubRowsAsEarlierReleases() {
        helper.createDatabase(TEST_DATABASE, 65).close()

        val db = helper.runMigrationsAndValidate(TEST_DATABASE, 66, true, migration_CardHub_65_66)

        assertEquals(RELEASED_GLOBAL_ROWS, db.globalRows())
        db.close()
    }

    @Test
    fun migration66To67WritesOnlyTheBuyXorRow() {
        helper.createDatabase(TEST_DATABASE, 65).close()
        helper.runMigrationsAndValidate(TEST_DATABASE, 66, true, migration_CardHub_65_66).use { db ->
            // A user who hid the referral banner on 66 keeps that choice through 66 -> 67.
            db.execSQL("UPDATE globalCardsHub SET visibility = 0 WHERE cardId = 'referral system'")
        }

        val db = helper.runMigrationsAndValidate(TEST_DATABASE, 67, true, migration_CardHub_66_67)

        assertEquals(
            listOf(
                GlobalRow("buy xor token", visibility = 1, sortOrder = 1, collapsed = 0),
                GlobalRow("get sora card", visibility = 1, sortOrder = 0, collapsed = 0),
                GlobalRow("referral system", visibility = 0, sortOrder = 2, collapsed = 0),
            ),
            db.globalRows(),
        )
        db.close()
    }

    private data class GlobalRow(
        val cardId: String,
        val visibility: Int,
        val sortOrder: Int,
        val collapsed: Int,
    )

    private fun SupportSQLiteDatabase.globalRows(): List<GlobalRow> =
        query("SELECT cardId, visibility, sortOrder, collapsed FROM globalCardsHub ORDER BY cardId")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            GlobalRow(
                                cardId = cursor.getString(0),
                                visibility = cursor.getInt(1),
                                sortOrder = cursor.getInt(2),
                                collapsed = cursor.getInt(3),
                            )
                        )
                    }
                }
            }

    private companion object {
        const val TEST_DATABASE = "card-hub-migration-rows-test.db"

        // What 3.8.x wrote from CardHubType.values().filter { !it.boundToAccount }.
        val RELEASED_GLOBAL_ROWS = listOf(
            GlobalRow("buy xor token", visibility = 1, sortOrder = 1, collapsed = 0),
            GlobalRow("get sora card", visibility = 1, sortOrder = 0, collapsed = 0),
            GlobalRow("referral system", visibility = 1, sortOrder = 2, collapsed = 0),
        )
    }
}
