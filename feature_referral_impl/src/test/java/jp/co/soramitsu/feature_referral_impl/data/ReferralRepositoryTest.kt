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

package jp.co.soramitsu.feature_referral_impl.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralReward
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ReferralRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var referralsDao: ReferralsDao

    @Mock
    private lateinit var blockExplorerManager: BlockExplorerManager

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Mock
    private lateinit var extrinsicManager: ExtrinsicManager

    @Mock
    private lateinit var substrateCalls: SubstrateCalls

    @Mock
    private lateinit var keypair: Sr25519Keypair

    @Mock
    private lateinit var feeToken: Token

    private lateinit var referralRepository: ReferralRepository

    private val REFERRER_REWARDS = listOf(
        ReferralReward(
            "address",
            "1000000000000000000"
        )
    )

    private val REFERRER_LOCAL = listOf(
        ReferralLocal(
            "address",
            "1000000000000000000"
        )
    )

    @Before
    fun setUp() = runTest {
        whenever(db.referralsDao()).thenReturn(referralsDao)
        referralRepository = ReferralRepositoryImpl(
            db,
            extrinsicManager,
            runtimeManager,
            substrateCalls,
            blockExplorerManager,
        )
    }

    @Test
    fun `update referral rewards called`() = runTest {
        val address = "address"
//        val captor = argumentCaptor<suspend () -> R>()
//        whenever(db.withTransaction(captor.capture())).thenReturn(captor.firstValue.invoke())

//        mockkStatic("androidx.room.RoomDatabaseKt")
//        val lambda = slot<suspend () -> R>()
//        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
//            lambda.captured.invoke()
//        }
        referralRepository.updateReferralRewards(address)

        verify(blockExplorerManager).updateReferrerRewards(address)
    }

    @Test
    fun `get referral rewards`() = runTest {
        whenever(referralsDao.getReferrals()).thenReturn(flow { emit(REFERRER_LOCAL) })
        val result = referralRepository.getReferralRewards()
        assertEquals(REFERRER_REWARDS, result.toList()[0])
    }

    @Test
    fun `referral mutation waits for canonical finality`() = runTest {
        given(
            extrinsicManager.submitAndWaitExtrinsic(
                anyString(),
                any(),
                anyBoolean(),
                anyString(),
                anyOrNull(),
                any(),
            )
        ).willReturn(
            ExtrinsicSubmitStatus(
                success = true,
                txHash = "0x${"11".repeat(32)}",
                blockHash = "0x${"22".repeat(32)}",
            )
        )

        referralRepository.observeSetReferrer(
            keypair = keypair,
            from = "from",
            referrer = "referrer",
            feeToken = feeToken,
        )
        referralRepository.observeBond(
            keypair = keypair,
            from = "from",
            amount = BigDecimal.ONE,
            token = feeToken,
            fee = BigDecimal.ZERO,
        )
        referralRepository.observeUnbond(
            keypair = keypair,
            from = "from",
            amount = BigDecimal.ONE,
            token = feeToken,
            fee = BigDecimal.ZERO,
        )

        verify(extrinsicManager, times(3)).submitAndWaitExtrinsic(
            from = eq("from"),
            keypair = eq(keypair),
            useBatchAll = eq(false),
            untilStatus = eq(SubstrateCalls.FINALIZED),
            activity = anyOrNull(),
            formExtrinsic = any(),
        )
    }
}
