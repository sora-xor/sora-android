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

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.util.EthereumAddressValidator
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
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
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

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

    @Mock
    private lateinit var qrCodeDecoder: QrCodeDecoder

    @Mock
    private lateinit var uri: Uri

    private lateinit var contactsViewModel: ContactsViewModel

    private val accountId = "accountId"
    private val accounts = mutableListOf("cnVko", "cnQwe")

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.select_account_address_1)).willReturn("Search address")
        given(avatar.createAvatar(anyString(), anyInt())).willReturn(drawable)
        given(walletInteractor.getContacts("")).willReturn(accounts)

        contactsViewModel = ContactsViewModel(
            walletInteractor, router, qrCodeDecoder,
            resourceManager, avatar,
        )
    }

    @Test
    fun `decode text from qr method and proccess result`() = runTest {
        advanceUntilIdle()
        val qrResponse = "response"
        given(qrCodeDecoder.decodeQrFromUri(uri)).willReturn(qrResponse)
        given(walletInteractor.processQr(qrResponse)).willReturn(
            Triple(
                "accountId",
                "0x020005",
                BigDecimal.ZERO
            )
        )
        contactsViewModel.decodeTextFromBitmapQr(uri)
        advanceUntilIdle()
        verify(router).showValTransferAmount(accountId, "0x020005")
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
