package jp.co.soramitsu.feature_wallet_impl.bridge

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTexts
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTransferAction
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTransferViewModel
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * The transfer page: every Ethereum step shows the most its gas can cost before the PIN, and two
 * pages of the same transfer (each its own view model) that both confirm sign once, within what
 * one sheet showed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HashiTransferPageConfirmationTest {

    @get:Rule
    val instant: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val main = MainCoroutineRule()

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()
    private val accounts = FakeAccounts()
    private val store = InMemoryStore()
    private val mainRouter = mockk<MainRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "s${firstArg<Int>()}" } }
    private val texts = HashiTexts(resources, NumbersFormatter())
    private val assets = mockk<AssetsInteractor> { coEvery { getCurSoraAccount() } returns testAccount }
    private val requestHash = ByteArray(32) { 0x11 }
    private val million = BigInteger.valueOf(1_000_000)
    private val destination: ByteArray get() = testAccount.substrateAddress.toAccountId()
    private val scopes = mutableListOf<CoroutineScope>()

    /** The app's one service; its background work runs on the test's clock. */
    private fun TestScope.service(): HashiBridgeService {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler)).also { scopes += it }
        return HashiBridgeService(sora, ethereum, accounts, store, pollIntervalMillis = 6_000, backgroundScope = scope, backgroundMonitoring = false)
    }

    private fun page(service: HashiBridgeService, id: String) = HashiTransferViewModel(
        SavedStateHandle(mapOf(HashiTransferViewModel.ARG_TRANSFER_ID to id)),
        service, assets, mainRouter, mockk(relaxed = true), resources, NumbersFormatter(),
    )

    private suspend fun HashiBridgeService.readyToClaim(): HashiTransferRecord {
        val sent = sendOutgoing(testAccount, quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, TestEthAccount.ADDRESS), "USDT")
        sora.approved[EthHex.encode(requestHash)] = HashiApprovedRequest(
            HashiCurrency.TokenAddress(USDT_TOKEN), million, TestEthAccount.address,
            ByteArray(20) { 3 }, requestHash, listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 1)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        return checkNotNull(refresh(testAccount, sent.id))
    }

    private fun gasShown(page: HashiTransferViewModel): String {
        val fee = page.state.value.confirm!!.fees.single()
        assertEquals("s${R.string.hashi_bridge_fee_ethereum}", fee.label)
        return fee.value
    }

    private fun finish() = scopes.forEach { it.cancel() }

    @Test
    fun `speed up shows its highest gas cost first, and two pages that both confirm sign one replacement at that cost`() = runTest {
        val service = service()
        val record = service.readyToClaim()
        service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val first = page(service, record.id)
        val second = page(service, record.id)
        advanceUntilIdle()
        assertTrue(first.state.value.speedUpEnabled && second.state.value.speedUpEnabled)

        first.onSpeedUp()
        advanceUntilIdle()
        // Gas is dearer by the time the second page opens its sheet.
        ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(2))
        second.onSpeedUp()
        advanceUntilIdle()
        val shownFirst = gasShown(first)
        val shownSecond = gasShown(second)
        assertEquals("s${R.string.hashi_bridge_speed_up}", first.state.value.confirm!!.title)
        // A sheet signs nothing; the PIN is asked only by Confirm.
        assertEquals(1, accounts.derivations)
        verify(exactly = 0) { mainRouter.showPin(any()) }
        first.onConfirm()
        second.onConfirm()
        verify(exactly = 2) { mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE) }

        first.onPinConfirmed()
        second.onPinConfirmed()
        advanceUntilIdle()
        val step = store.get(testAccount.substrateAddress, record.id)!!.step(HashiEthIntent.CLAIM)!!
        assertEquals(2, step.attempts.size)
        assertEquals(2, accounts.derivations)
        assertEquals(2, ethereum.sent.size)
        // What can be paid at most is what the first page showed, not the second page's figure.
        assertEquals(shownFirst, texts.eth(step.maximumCost))
        assertTrue(shownFirst != shownSecond)
        assertNull(first.state.value.error)
        assertEquals("s${R.string.hashi_bridge_error_pending}", second.state.value.error)
        assertNull(first.state.value.confirm)
        assertNull(second.state.value.confirm)

        // The same PIN result delivered again does nothing.
        first.onPinConfirmed()
        second.onPinConfirmed()
        advanceUntilIdle()
        assertEquals(2, accounts.derivations)
        assertEquals(2, ethereum.sent.size)
        finish()
    }

    @Test
    fun `receive on Ethereum confirmed on two pages signs one claim, at the gas its sheet showed`() = runTest {
        val service = service()
        val record = service.readyToClaim()
        val first = page(service, record.id)
        val second = page(service, record.id)
        advanceUntilIdle()
        assertEquals(HashiTransferAction.CLAIM, first.state.value.action)
        first.onPrimaryAction()
        second.onPrimaryAction()
        advanceUntilIdle()
        val shown = gasShown(first)
        assertEquals("s${R.string.hashi_bridge_receive_on_ethereum}", first.state.value.confirm!!.title)
        assertEquals(0, accounts.derivations)
        ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(3))
        first.onPinConfirmed()
        second.onPinConfirmed()
        advanceUntilIdle()
        val claim = store.get(testAccount.substrateAddress, record.id)!!.step(HashiEthIntent.CLAIM)!!
        assertEquals(1, claim.attempts.size)
        assertEquals(1, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
        assertEquals(shown, texts.eth(claim.maximumCost))
        assertEquals("s${R.string.hashi_bridge_error_pending}", second.state.value.error)
        finish()
    }

    @Test
    fun `continue on Ethereum shows the gas first, and two pages that both confirm deposit once within it`() = runTest {
        val service = service()
        // An approval was mined; the app was closed before the deposit.
        ethereum.failNextSends(java.io.IOException("connection lost"))
        val started = runCatching { service.startIncoming(testAccount, service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE), "USDT") }
        val record = started.getOrNull() ?: store.transfers(testAccount.substrateAddress).single()
        ethereum.sendFailure = null
        service.refreshOpen()
        ethereum.confirm(store.get(testAccount.substrateAddress, record.id)!!.step(HashiEthIntent.APPROVE)!!.latest.hash, emptyList())
        ethereum.allowance = million
        service.refreshOpen()
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, store.get(testAccount.substrateAddress, record.id)!!.state)
        ethereum.onSend = { _ ->
            val step = store.history.last().ethSteps.last()
            ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, million, USDT_TOKEN)))
        }
        val derivations = accounts.derivations
        val sent = ethereum.sent.size

        val first = page(service, record.id)
        val second = page(service, record.id)
        advanceUntilIdle()
        assertEquals(HashiTransferAction.CONTINUE_DEPOSIT, first.state.value.action)
        first.onPrimaryAction()
        second.onPrimaryAction()
        advanceUntilIdle()
        val shown = gasShown(first)
        assertEquals("s${R.string.hashi_bridge_continue_deposit}", first.state.value.confirm!!.title)
        assertNotNull(second.state.value.confirm)
        assertEquals(derivations, accounts.derivations)

        first.onPinConfirmed()
        second.onPinConfirmed()
        advanceUntilIdle()
        val saved = store.get(testAccount.substrateAddress, record.id)!!
        val deposits = saved.ethSteps.filter { it.intent == HashiEthIntent.DEPOSIT }
        assertEquals(1, deposits.size)
        assertEquals(1, deposits.single().attempts.size)
        assertEquals(derivations + 1, accounts.derivations)
        assertEquals(sent + 1, ethereum.sent.size)
        assertEquals(shown, texts.eth(deposits.single().maximumCost))
        assertEquals(HashiEthStepState.CONFIRMED, deposits.single().state)
        finish()
    }
}
