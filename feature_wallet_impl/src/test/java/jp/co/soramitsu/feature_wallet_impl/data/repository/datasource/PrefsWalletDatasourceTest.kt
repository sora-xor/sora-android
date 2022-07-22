/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PrefsWalletDatasourceTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var soraPreferences: SoraPreferences

    @Mock
    private lateinit var encryptedPreferences: EncryptedPreferences

    private lateinit var prefsWalletDatasource: PrefsWalletDatasource
    private val emptyJson = "{}"

    @Before
    fun setUp() {
        prefsWalletDatasource = PrefsWalletDatasource(soraPreferences, encryptedPreferences)
    }

    @Test
    fun `observe migration`() = runTest {
        prefsWalletDatasource.saveMigrationStatus(MigrationStatus.SUCCESS)
        verify(soraPreferences).putString(
            "key_migration_status",
            MigrationStatus.SUCCESS.toString()
        )
        val first = prefsWalletDatasource.observeMigrationStatus().first()
        assertEquals(MigrationStatus.SUCCESS, first)
    }

    @Test
    fun `save tx block`() = runTest {
        prefsWalletDatasource.saveClaimBlockAndTxHash("block", "hash")
        verify(encryptedPreferences).putEncryptedString("key_claim_block_hash", "block")
        verify(encryptedPreferences).putEncryptedString("key_claim_tx_hash", "hash")
    }
}