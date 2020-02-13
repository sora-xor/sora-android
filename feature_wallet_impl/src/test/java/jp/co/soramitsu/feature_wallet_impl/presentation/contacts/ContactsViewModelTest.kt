package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactListItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactMenuItem
import jp.co.soramitsu.test_shared.RxSchedulersRule
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
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class ContactsViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: WalletInteractor
    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var preloader: WithPreloader
    @Mock private lateinit var resourceManager: ResourceManager

    @Mock private lateinit var qrCodeDecoder: QrCodeDecoder
    @Mock private lateinit var uri: Uri

    private lateinit var contactsViewModel: ContactsViewModel

    private val accountId = "accountId"
    private val firstName = "firstName"
    private val lastName = "lastName"
    private var account = Account(firstName, lastName, accountId)
    private val amount = "1000"
    private val accounts = mutableListOf(account)
    private val expected = mutableListOf(
        ContactMenuItem(R.drawable.ic_scan, R.string.contacts_scan_qr, ContactMenuItem.Type.SCAN_QR_CODE),
        ContactHeader("Contacts"),
        ContactListItem(account, true)
    )

    @Before fun setUp() {
        given(resourceManager.getString(R.string.contacts_title)).willReturn("Contacts")

        contactsViewModel = ContactsViewModel(interactor, router, preloader, qrCodeDecoder, resourceManager)
    }

    @Test fun `back button clicked`() {
        contactsViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test fun `open camera event`() {
        contactsViewModel.openCamera()

        contactsViewModel.initiateScannerLiveData.observeForever {
            assertEquals(Unit, it.peekContent())
        }
    }

    @Test fun `open gallery event`() {
        contactsViewModel.openGallery()

        contactsViewModel.initiateGalleryChooserLiveData.observeForever {
            assertEquals(Unit, it.peekContent())
        }
    }

    @Test fun `decode text from qr method and proccess result`() {
        val assetId = "XOR"
        val qrResponse = "response"

        given(qrCodeDecoder.decodeQrFromUri(uri)).willReturn(Single.just(qrResponse))
        given(interactor.processQr(qrResponse)).willReturn(Single.just(Pair(BigDecimal.ZERO, account)))

        contactsViewModel.decodeTextFromBitmapQr(uri)

        verify(router).showTransferAmount(accountId, "$firstName $lastName", BigDecimal.ZERO)
    }

    @Test fun `fetch contacts`() {
        given(interactor.getContacts(true)).willReturn(Single.just(accounts))

        contactsViewModel.getContacts(true, true)

        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()

        contactsViewModel.contactsLiveData.observeForever {
            assertEquals(expected, it)
        }
    }

    @Test fun `search event`() {
        val query = "query"

        given(interactor.findOtherUsersAccounts(query)).willReturn(Single.just(accounts))

        contactsViewModel.search(query)

        contactsViewModel.contactsLiveData.observeForever {
            assertEquals(expected, it)
        }
    }
}