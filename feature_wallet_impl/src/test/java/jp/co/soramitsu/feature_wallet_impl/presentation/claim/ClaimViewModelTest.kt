package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.atLeast
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ClaimViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: ClaimViewModel

    @Before
    fun setUp() {
        given(walletInteractor.observeMigrationStatus()).willReturn(Observable.just(MigrationStatus.SUCCESS))
        viewModel = ClaimViewModel(router, walletInteractor, resourceManager)
    }

    @Test
    fun `check init`() {
        verify(router).popBackStackFragment()
    }

    @Test
    fun `contacts us click`() {
        val c = "contacts"
        given(resourceManager.getString(anyInt())).willReturn(c)
        viewModel.contactsUsClicked()
        viewModel.openSendEmailEvent.observeForever {
            assertEquals(c, it)
        }
    }

    @Test
    fun `next click`() {
        given(walletInteractor.needsMigration()).willReturn(Single.just(false))
        viewModel.checkMigrationIsAlreadyFinished()
        verify(router, times(2)).popBackStackFragment()
    }
}