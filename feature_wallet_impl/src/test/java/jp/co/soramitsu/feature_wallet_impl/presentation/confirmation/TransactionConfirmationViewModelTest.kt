package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
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
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.times
import org.mockito.Mock
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransactionConfirmationViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var ethInteractor: EthereumInteractor

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var textFormatter: TextFormatter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var clip: ClipboardManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var mockOb: Observer<Unit>

    private lateinit var viewModel: TransactionConfirmationViewModel

    @Before
    fun setUp() {
        given(walletInteractor.getAssets()).willReturn(Single.just(assetList()))
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("0.34")
        viewModel = TransactionConfirmationViewModel(
            walletInteractor,
            ethInteractor,
            router,
            progress,
            resourceManager,
            numbersFormatter,
            textFormatter,
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            "id2",
            "peerFullName",
            "peerId",
            TransferType.VAL_TRANSFER,
            "",
            clip,
        )
    }

    @Test
    fun `test init`() {
        viewModel.balanceFormattedLiveData.observeForever {
            assertEquals("0.34", it)
        }
        viewModel.inputTokenLastNameLiveData.observeForever {
            assertEquals("pswap", it)
        }
    }

    @Test
    fun `back click`() {
        viewModel.backButtonPressed()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `copy address click`() {
        viewModel.copyAddress()
        viewModel.copiedAddressEvent.observeForever(mockOb)
        verify(mockOb, times(1)).onChanged(Unit)
    }

    @Test
    fun `next click`() {
        given(
            walletInteractor.observeTransfer(
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(
            Completable.complete()
        )
        viewModel.nextClicked()
        verify(router).returnToWalletFragment()
    }

    private fun assetList() = listOf(
        Asset(
            "id",
            "sora",
            "val",
            true,
            true,
            1,
            4,
            18,
            BigDecimal.TEN,
            0,
            true
        ),
        Asset(
            "id2",
            "polkaswap",
            "pswap",
            true,
            true,
            2,
            4,
            18,
            BigDecimal.TEN,
            0,
            true
        )
    )
}
