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

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ContactsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var avatar: AccountAvatarGenerator

    @Mock
    private lateinit var drawable: PictureDrawable

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var contactsViewModel: ContactsViewModel

    private val accountId = "accountId"
    private val accounts = mutableListOf("cnVko", "cnQwe")

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.select_account_address_1)).willReturn("Search address")
        given(avatar.createAvatar(anyString(), anyInt())).willReturn(drawable)
        given(walletInteractor.getContacts("")).willReturn(accounts)

        contactsViewModel = ContactsViewModel(
            walletInteractor, router,
            resourceManager, avatar,
        )
    }

    @Test
    fun `fetch contacts`() = runTest {
        advanceUntilIdle()
        val state = contactsViewModel.state
        assertEquals(2, state.accounts.size)
    }

    @Test
    fun `search event`() = runTest {
        advanceUntilIdle()
        val query = "query"
        given(walletInteractor.getContacts(query)).willReturn(accounts)
        contactsViewModel.search(TextFieldValue(query))
        advanceUntilIdle()
        val state = contactsViewModel.state
        assertEquals(2, state.accounts.size)
    }
}
