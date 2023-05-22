/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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