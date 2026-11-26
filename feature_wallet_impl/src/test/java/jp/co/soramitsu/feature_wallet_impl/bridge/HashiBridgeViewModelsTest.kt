package jp.co.soramitsu.feature_wallet_impl.bridge

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.createAssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiBlock
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiAvailabilityUi
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiBridgeViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTransferAction
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTransferViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@OptIn(ExperimentalCoroutinesApi::class)
class HashiBridgeViewModelsTest {

    @get:Rule
    val instant: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val main = MainCoroutineRule()

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()
    private val accounts = FakeAccounts()
    private val store = InMemoryStore()
    private val service = HashiBridgeService(sora, ethereum, accounts, store, pollIntervalMillis = 0)
    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val mainRouter = mockk<MainRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "s${firstArg<Int>()}" } }
    private val usdt = Token(USDT_ID, "Tether", "USDT", 18, true, null, null, null, null)
    private val assets = mockk<AssetsInteractor> {
        coEvery { getCurSoraAccount() } returns testAccount
        coEvery { getWhitelistAssets() } returns listOf(
            Asset(usdt, false, 0, createAssetBalance(BigDecimal.TEN), true),
        )
    }

    private fun bridgeViewModel(assetId: String? = USDT_ID) = HashiBridgeViewModel(
        SavedStateHandle(if (assetId != null) mapOf(HashiBridgeViewModel.ARG_ASSET_ID to assetId) else emptyMap()),
        service, assets, walletRouter, mainRouter, mockk<BasicClipboardManager>(relaxed = true), resources, NumbersFormatter(),
    )

    @Test
    fun `today's paused bridge shows the banner and keeps Continue off`() = runTest {
        sora.blocks = setOf(HashiBlock.SIGNERS_UNVERIFIED)
        val viewModel = bridgeViewModel()
        advanceUntilIdle()
        viewModel.onAmount(BigDecimal.ONE)
        val state = viewModel.state.value
        assertEquals(HashiAvailabilityUi.PAUSED, state.availability)
        assertNotNull(state.input)
        assertEquals(TestEthAccount.ADDRESS, state.recipient)
        assertFalse(state.canContinue)
        viewModel.onContinue()
        advanceUntilIdle()
        assertNull(viewModel.state.value.confirm)
        assertTrue(sora.signed.isEmpty())
    }

    @Test
    fun `a failed status read is unreadable, not open`() = runTest {
        val failing = object : HashiSoraGateway by sora {
            override suspend fun availability() = throw java.io.IOException("offline")
        }
        val viewModel = HashiBridgeViewModel(
            SavedStateHandle(), HashiBridgeService(failing, ethereum, accounts, store), assets, walletRouter, mainRouter,
            mockk(relaxed = true), resources, NumbersFormatter(),
        )
        advanceUntilIdle()
        assertEquals(HashiAvailabilityUi.UNREADABLE, viewModel.state.value.availability)
        assertFalse(viewModel.state.value.canContinue)
    }

    @Test
    fun `Continue quotes, Confirm asks for the PIN and only the PIN result sends`() = runTest {
        val viewModel = bridgeViewModel()
        advanceUntilIdle()
        assertEquals(HashiAvailabilityUi.OPEN, viewModel.state.value.availability)
        // The amount field takes USDT's 6 Ethereum decimals.
        assertEquals(6, viewModel.state.value.input!!.token.precision)
        viewModel.onAmount(BigDecimal("1.5"))
        assertTrue(viewModel.state.value.canContinue)
        viewModel.onContinue()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.confirm)
        assertTrue(sora.signed.isEmpty())
        viewModel.onConfirm()
        verify(exactly = 1) { mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE) }
        assertTrue(sora.signed.isEmpty())
        viewModel.onPinConfirmed()
        advanceUntilIdle()
        assertEquals(1, sora.submitted.size)
        val record = store.transfers(testAccount.substrateAddress).single()
        assertEquals("1500000", record.ethAmount)
        viewModel.onScreenResumed()
        verify { walletRouter.openHashiBridgeTransfer(record.id) }
    }

    @Test
    fun `a refused quote is explained and nothing is signed`() = runTest {
        sora.locked[USDT_ID] = BigInteger.ONE
        val viewModel = bridgeViewModel()
        advanceUntilIdle()
        viewModel.onAmount(BigDecimal.ONE)
        viewModel.onContinue()
        advanceUntilIdle()
        assertEquals("s${jp.co.soramitsu.common.R.string.hashi_bridge_error_liquidity}", viewModel.state.value.notice)
        assertNull(viewModel.state.value.confirm)
        assertTrue(sora.signed.isEmpty())
    }

    @Test
    fun `an account without a recovery phrase has no Ethereum account and cannot continue`() = runTest {
        accounts.address = null
        val viewModel = bridgeViewModel()
        advanceUntilIdle()
        viewModel.onAmount(BigDecimal.ONE)
        assertTrue(viewModel.state.value.noEthereumAccount)
        assertFalse(viewModel.state.value.canContinue)
    }

    @Test
    fun `the progress page claims an approved withdrawal through the PIN, even while paused`() = runTest {
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, TestEthAccount.ADDRESS)
        val record = service.sendOutgoing(testAccount, quote, "USDT")
        val requestHash = EthHex.decode(record.requestHash!!)
        sora.approved[record.requestHash!!] = HashiApprovedRequest(
            HashiCurrency.TokenAddress(USDT_TOKEN), BigInteger.valueOf(1_000_000), TestEthAccount.address,
            ByteArray(20) { 3 }, requestHash, listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 1)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        service.refresh(testAccount, record.id)
        sora.blocks = setOf(HashiBlock.SIGNERS_DOWN)
        val viewModel = HashiTransferViewModel(
            SavedStateHandle(mapOf(HashiTransferViewModel.ARG_TRANSFER_ID to record.id)),
            service, assets, mainRouter, mockk(relaxed = true), resources, NumbersFormatter(),
        )
        advanceUntilIdle()
        assertEquals(HashiTransferAction.CLAIM, viewModel.state.value.action)
        viewModel.onPrimaryAction()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.confirm)
        assertTrue(ethereum.sent.isEmpty())
        viewModel.onConfirm()
        verify(exactly = 1) { mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE) }
        viewModel.onPinConfirmed()
        advanceUntilIdle()
        assertEquals(1, ethereum.sent.size)
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, store.get(testAccount.substrateAddress, record.id)!!.state)
        assertTrue(viewModel.state.value.canSpeedUp)
        assertEquals(HashiBridgeContract.BRIDGE_ADDRESS.lowercase(), store.get(testAccount.substrateAddress, record.id)!!.step(HashiEthIntent.CLAIM)!!.to)
    }
}
