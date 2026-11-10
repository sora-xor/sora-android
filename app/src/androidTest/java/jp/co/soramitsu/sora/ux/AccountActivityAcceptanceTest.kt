@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.ux

import jp.co.soramitsu.sora.testutil.NativeDisplayCapture

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders the real activity feed with deterministic, read-only transaction fixtures. */
@RunWith(AndroidJUnit4::class)
class AccountActivityAcceptanceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

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
            compose.onNodeWithText(context.getString(R.string.activity_empty_content_title), useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText(sent.transactionHash, substring = true, useUnmergedTree = true).assertDoesNotExist()

            compose.onNodeWithText(sentLabel, substring = true).performClick()
            // SelectionContainer exposes its own semantics beneath the clickable row.
            // Check the actual rendered text nodes instead of the merged accessibility ancestor.
            compose.onNodeWithText(
                context.getString(R.string.network_history_transaction_hash, sent.transactionHash),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            compose.onNodeWithText(
                context.getString(R.string.network_history_counterparty, counterparty),
                useUnmergedTree = true,
            ).assertIsDisplayed()
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
            compose.onNodeWithText(context.getString(R.string.activity_empty_content_title), useUnmergedTree = true).assertDoesNotExist()
            val loadMore = context.getString(R.string.nexus_history_load_more)
            compose.onNodeWithText(loadMore).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(1, paginationCalls.get())
                assertEquals(account, requestedAccount.get())
            }
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
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)[0].assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "ux-acceptance").apply { mkdirs() }
        val bitmap = NativeDisplayCapture.capture()
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
