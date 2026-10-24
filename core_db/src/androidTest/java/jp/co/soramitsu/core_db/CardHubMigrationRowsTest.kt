/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
