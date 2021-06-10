/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactListItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.util.EthereumAddressValidator
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
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

@RunWith(MockitoJUnitRunner::class)
class ContactsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor
    @Mock
    private lateinit var ethereumInteractor: EthereumInteractor
    @Mock
    private lateinit var router: WalletRouter
    @Mock
    private lateinit var preloader: WithPreloader
    @Mock
    private lateinit var avatar: AccountAvatarGenerator
    @Mock
    private lateinit var drawable: PictureDrawable
    @Mock
    private lateinit var resourceManager: ResourceManager
    @Mock
    private lateinit var ethereumAddressValidator: EthereumAddressValidator

    @Mock
    private lateinit var qrCodeDecoder: QrCodeDecoder
    @Mock
    private lateinit var uri: Uri

    private lateinit var contactsViewModel: ContactsViewModel

    private val accountId = "accountId"
    private val address = "address"
    private val firstName = "firstName"
    private val lastName = "lastName"
    private var account = Account(firstName, lastName, accountId)
    private val amount = "1000"
    private val accounts = mutableListOf(account)

    @Before
    fun setUp() {
        given(avatar.createAvatar(anyString(), anyInt())).willReturn(drawable)

        contactsViewModel = ContactsViewModel(
            walletInteractor, router, preloader, qrCodeDecoder,
            resourceManager, ethereumAddressValidator, ethereumInteractor, avatar
        )
    }

    @Test
    fun `back button clicked`() {
        contactsViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test
    fun `open camera event`() {
        contactsViewModel.openCamera()

        val r = contactsViewModel.initiateScanner.getOrAwaitValue()
        assertEquals(Unit, r)
    }

    @Test
    fun `open gallery event`() {
        contactsViewModel.openGallery()

        val r = contactsViewModel.chooseGallery.getOrAwaitValue()
        assertEquals(Unit, r)
    }

    @Test
    fun `decode text from qr method and proccess result`() {
        val qrResponse = "response"

        given(qrCodeDecoder.decodeQrFromUri(uri)).willReturn(Single.just(qrResponse))
        given(walletInteractor.processQr(qrResponse)).willReturn(
            Single.just(
                Triple(
                    "accountId",
                    "firstName lastName",
                    BigDecimal.ZERO
                )
            )
        )

        contactsViewModel.decodeTextFromBitmapQr(uri)

        verify(router).showValTransferAmount(accountId, "$firstName $lastName", BigDecimal.ZERO)
    }

    @Test
    fun `fetch contacts`() {
        given(walletInteractor.getContacts("")).willReturn(Single.just(accounts))
        val expected = mutableListOf(
            ContactListItem(account, drawable, true)
        )
        contactsViewModel.getContacts(true)

        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()

        contactsViewModel.contactsLiveData.observeForever {
            assertEquals(expected, it)
        }
    }

    @Test
    fun `search event`() {
        val query = "query"
        val expected = mutableListOf(
            ContactListItem(account, drawable, true)
        )
        given(walletInteractor.findOtherUsersAccounts(query)).willReturn(Single.just(accounts))

        contactsViewModel.search(query)

        contactsViewModel.contactsLiveData.observeForever {
            assertEquals(expected, it)
        }
    }

    @Test
    fun `qr toolbar menu clicked`() {
        contactsViewModel.qrMenuItemClicked()

        val r = contactsViewModel.showChooser.getOrAwaitValue()
        assertEquals(Unit, r)
    }
}