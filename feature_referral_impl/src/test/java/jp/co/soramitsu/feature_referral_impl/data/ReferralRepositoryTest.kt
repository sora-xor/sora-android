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

package jp.co.soramitsu.feature_referral_impl.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.referral.ReferrerReward
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
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
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

    private lateinit var referralRepository: ReferralRepository

    private val REFERRER_REWARDS = listOf(
        ReferrerReward(
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
}
