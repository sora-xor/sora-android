@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.ux

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.walletDateTime
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen.AccountActivityScreen
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen.NetworkActivityState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Renders the real activity feed with deterministic, read-only transaction fixtures. */
@RunWith(AndroidJUnit4::class)
class AccountActivityAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val accountAddress = "SampleAccountForActivityAcceptance"
    private val counterparty = "SampleCounterpartyForActivityAcceptance"
    private val sent = NexusTransferHistoryItem(
        transactionHash = "a1".repeat(32),
        timestampMillis = Instant.parse("2026-10-13T03:15:00Z").toEpochMilli(),
        amount = "12.5",
        sender = accountAddress,
        receiver = counterparty,
    )
    private val received = NexusTransferHistoryItem(
        transactionHash = "b2".repeat(32),
        timestampMillis = sent.timestampMillis - 3_600_000,
        amount = "3.25",
        sender = counterparty,
        receiver = accountAddress,
    )

    @Before
    fun enableTestProcessDrawing() {
        instrumentation.runOnMainSync { HardwareRenderer.setDrawingEnabled(true) }
    }

    @Test
    fun tairaHistoryIsVisibleWithNoSora2TransactionsAndExpandsDetails() {
        val account = account()
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            render(scenario, HistoryState.NoData, account)

            val sentLabel = "${context.getString(R.string.common_sent)} · 12.5 XOR"
            val receivedLabel = "${context.getString(R.string.common_received)} · 3.25 XOR"
            assertVisible(sentLabel)
            assertVisible(receivedLabel)
            assertVisible("Taira · ${context.getString(R.string.network_badge_testnet)}")
            assertVisible(walletDateTime(sent.timestampMillis))
            assertVisible(walletDateTime(received.timestampMillis))
            assertVisible(context.getString(R.string.wallet_status_confirmed))
            assertNull(findText(context.getString(R.string.activity_empty_content_title)))
            assertNull(findText(sent.transactionHash))

            clickText(sentLabel)
            assertVisible(context.getString(R.string.network_history_transaction_hash, sent.transactionHash))
            assertVisible(context.getString(R.string.network_history_counterparty, counterparty))
            capture("account-activity")
        }
    }

    @Test
    fun sora2ErrorKeepsTairaHistoryVisibleAndPaginationUsesItsAccount() {
        val account = account().copy(confirmedTransfers = listOf(sent), hasMore = true)
        val paginationCalls = AtomicInteger()
        val requestedAccount = AtomicReference<NetworkAccountActivity>()
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            render(scenario, HistoryState.Error, account) {
                requestedAccount.set(it)
                paginationCalls.incrementAndGet()
            }

            assertVisible("${context.getString(R.string.common_sent)} · 12.5 XOR")
            assertVisible(context.getString(R.string.activity_error_title))
            assertNull(findText(context.getString(R.string.activity_empty_content_title)))
            val loadMore = context.getString(R.string.nexus_history_load_more)
            revealText(loadMore)
            clickText(loadMore)
            waitUntil { paginationCalls.get() == 1 }
            assertEquals(1, paginationCalls.get())
            assertEquals(account, requestedAccount.get())
        }
    }

    private fun render(
        scenario: ActivityScenario<OnboardingActivity>,
        history: HistoryState,
        account: NetworkAccountActivity,
        onLoadMore: (NetworkAccountActivity) -> Unit = {},
    ) {
        scenario.onActivity { activity ->
            activity.setContent {
                SoraAppTheme {
                    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.x2)) {
                        Text(
                            stringResource(R.string.common_activity),
                            modifier = Modifier.padding(top = Dimens.x3),
                            style = MaterialTheme.customTypography.headline1,
                            color = MaterialTheme.customColors.fgPrimary,
                        )
                        AccountActivityScreen(
                            modifier = Modifier.padding(top = Dimens.x2).fillMaxSize(),
                            historyState = history,
                            networkActivity = NetworkActivityState(
                                walletId = account.walletId,
                                accounts = listOf(account),
                                loading = false,
                            ),
                            onRefreshClick = {},
                            onHistoryItemClick = {},
                            onMoreHistoryItemRequested = {},
                            onMoreNetworkHistoryRequested = onLoadMore,
                        )
                    }
                }
            }
        }
    }

    private fun account() = NetworkAccountActivity(
        walletId = "activity-acceptance-wallet",
        networkId = WalletNetworkId.TAIRA,
        networkName = "Taira",
        isTestnet = true,
        address = accountAddress,
        confirmedTransfers = listOf(sent, received),
        pendingTransactions = emptyList(),
        errorCode = null,
        hasMore = false,
    )

    private fun assertVisible(text: String) {
        waitUntil { findText(text)?.isVisibleToUser == true }
        assertTrue("Expected visible activity text: $text", findText(text)?.isVisibleToUser == true)
    }

    private fun findText(text: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString()?.contains(text) == true ||
                node.contentDescription?.toString()?.contains(text) == true) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { visit(it)?.let { match -> return match } }
            }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }

    private fun clickText(text: String) {
        var target = findText(text) ?: error("Missing activity action: $text")
        while (!target.isClickable) target = target.parent ?: error("Activity action is not clickable: $text")
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $text" }
        instrumentation.waitForIdleSync()
    }

    private fun revealText(text: String) {
        repeat(12) {
            findText(text)?.let { node ->
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                instrumentation.waitForIdleSync()
                if (findText(text)?.isVisibleToUser == true) return
            }
            fun scroll(node: AccessibilityNodeInfo): Boolean {
                if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { if (scroll(it)) return true }
                }
                return false
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::scroll)
            instrumentation.waitForIdleSync()
            Thread.sleep(100)
        }
        error("Cannot reveal activity action: $text")
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            if (System.nanoTime() >= deadline) {
                capture("account-activity-failure")
                error("Activity UI condition timed out")
            }
            Thread.sleep(100)
        }
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String) {
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { it.invalidate() }
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(1200)
        val directory = File(context.getExternalFilesDir(null), "ux-acceptance").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
