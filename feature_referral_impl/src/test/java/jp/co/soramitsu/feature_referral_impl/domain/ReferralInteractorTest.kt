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

package jp.co.soramitsu.feature_referral_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralReward
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
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
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ReferralInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var referralRepository: ReferralRepository

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var walletRepository: WalletRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    private lateinit var interactor: ReferralInteractor

    private val REFERRER_REWARDS = listOf(
        ReferralReward(
            "address",
            "1000000000000000000"
        )
    )

    private val REFERRALS = listOf(
        Referral(
            "address",
            BigDecimal.ONE
        )
    )

    @Before
    fun setUp() = runTest {
        interactor = ReferralInteractor(
            assetsRepository,
            userRepository,
            referralRepository,
            walletRepository,
            credentialsRepository,
            runtimeManager,
            transactionHistoryRepository
        )
    }

    @Test
    fun `update referrals called`() = runTest {
        val soraAcc = SoraAccount("address", "account")
        given(userRepository.getCurSoraAccount()).willReturn(soraAcc)

        interactor.updateReferrals()

        verify(referralRepository).updateReferralRewards(soraAcc.substrateAddress)
    }

    @Test
    fun `get referrals called`() = runTest {
        given(referralRepository.getReferralRewards()).willReturn(flow { emit(REFERRER_REWARDS) })

        val result = interactor.getReferrals()

        assertEquals(result.toList()[0], REFERRALS)
    }
}
