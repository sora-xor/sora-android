@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.portfolio

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.components.Toolbar
import jp.co.soramitsu.common.presentation.compose.components.initNoToolbar
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsPage
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.AssetDetailsScreen
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetCardStateData
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_wallet_impl.data.nexus.*
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import jp.co.soramitsu.sora.testutil.NativeDisplayCapture
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.theme.customColors
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshots only, run on request (`-e nexusCapture true`): the SORA2 XOR page and the Taira
 * XOR pages with the same sample amounts, each in the frame SoraBaseFragment draws (background,
 * toolbar), for review images. The pages' behaviour is tested by NexusXorPagesTest.
 */
@RunWith(AndroidJUnit4::class)
class NexusXorPagesCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owner = "testuﾛ1PﾖCｺk9iﾕｷ2AﾇｻﾀﾂBvﾉﾅKRｴjｷﾆWﾂiｻｾnﾈkLFｺpzﾗYQ5R19"
    private val peer = "testuﾛ1NhｶﾅSamplePeerAddressForReviewOnly"
    private val directory by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "nexus-capture").apply { mkdirs() }
    }

    @Before fun onlyOnRequest() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nexusCapture") == "true")
    }

    private fun taira() = NexusPortfolioBalance(
        walletId = "sample", networkId = WalletNetworkId.TAIRA, networkName = "Taira Testnet", isTestnet = true,
        address = owner, xorQuantity = "25000", assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
        explorerBaseUrl = TairaTestnetContract.TORII_ROOT, sendAvailable = true, pendingTransactions = emptyList(),
        confirmedTransfers = listOf(
            NexusTransferHistoryItem("ab".repeat(32), 1_794_000_000_000, "12.5", owner, peer),
            NexusTransferHistoryItem("cd".repeat(32), 1_793_900_000_000, "25000", "testuﾛ1Faucet", owner),
        ),
        historyErrorCode = null, errorCode = null, historyHasMore = true,
    )

    @Composable
    private fun Page(toolbar: SoramitsuToolbarState, content: @Composable (ScrollState) -> Unit) {
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            NeonBackground()
            Scaffold(
                backgroundColor = Color.Transparent,
                topBar = {
                    Toolbar(toolbarState = toolbar, scrollState = scroll, backgroundColor = Color.Transparent,
                        tintColor = MaterialTheme.customColors.fgPrimary)
                },
            ) { padding -> Box(Modifier.padding(padding)) { content(scroll) } }
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { SoraAppTheme { content() } }
        compose.waitForIdle()
        Thread.sleep(1_500)
        val bitmap = NativeDisplayCapture.capture()
        try {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun sora2XorPage() = shoot("fixture-sora2-xor-details") {
        Page(initSmallTitle2("")) { scroll ->
            AssetDetailsPage(refreshing = false, onRefresh = {}) {
                AssetDetailsScreen(
                    AssetCardStateData(
                        tokenId = "0x0200000000000000000000000000000000000000000000000000000000000000",
                        tokenName = "SORA", tokenIcon = DEFAULT_ICON_URI, tokenSymbol = "XOR", price = "$0.0000021",
                        priceChange = "-1.2%", transferableBalance = "25 000 XOR", transferableBalanceFiat = "$0.05",
                        poolsCardTitle = "", poolsState = PoolsListState(emptyList()), poolsSum = "",
                        isTransferableBalanceAvailable = true,
                        events = listOf(
                            EventUiModel.EventTxUiModel.EventTransferOutUiModel("a", DEFAULT_ICON_URI, "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
                                "", 1, "12.5 XOR", "", TransactionStatus.COMMITTED),
                            EventUiModel.EventTxUiModel.EventTransferInUiModel("b", 1, TransactionStatus.COMMITTED, DEFAULT_ICON_URI,
                                "cnTQ1kbv7PBNNQrEb1tZpmK7hZnwvhmLVfshNbRf2YkbbTZqK", "", "+25 000 XOR", ""),
                        ),
                    ),
                    scroll, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
    }

    @Test fun tairaXorPage() = shoot("fixture-taira-xor-details") {
        Page(initSmallTitle2("")) { scroll ->
            NexusAssetScreen(taira(), DEFAULT_ICON_URI, "SORA", scroll, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    private fun prepared(): NexusPreparedSend {
        val account = jp.co.soramitsu.core_db.model.NetworkAccountLocal("sample", "taira", "00", owner, "", 1, true)
        val request = NexusTransferSigningRequest(NexusNetworks.taira, account, peer, "xor", "12.5")
        val quote = NexusTransferFeeQuote("taira", owner, peer, "xor", "12.5", "0.01", "fixture", 123)
        return NexusPreparedSend(NexusSendRequest("sample", WalletNetworkId.TAIRA, peer, "12.5"),
            peer, "12.5", "xor", "0.01", "25000", "fixture", request, quote)
    }

    private fun send(state: NexusSendUiState, toolbar: SoramitsuToolbarState, name: String) = shoot(name) {
        Page(toolbar) { scroll ->
            NexusSendScreen(state, DEFAULT_ICON_URI, "SORA", scroll, { null }, { "08 Nov. 2026, 04:30" },
                {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test fun tairaSendForm() = send(
        NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "12.5"),
        initSmallTitle2(R.string.wallet_send_xor), "fixture-taira-send-form",
    )

    @Test fun tairaSendReview() = send(
        NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "12.5", prepared = prepared()),
        initSmallTitle2(R.string.confirm_sending), "fixture-taira-send-review",
    )

    @Test fun tairaSendOutcome() = send(
        NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "12.5", prepared = prepared(),
            result = NexusSendResult("local", "ef".repeat(32), "COMMITTED")),
        initNoToolbar(), "fixture-taira-send-outcome",
    )

    @Test fun tairaTransferPage() = shoot("fixture-taira-transfer-details") {
        Page(initNoToolbar()) { scroll ->
            val balance = taira()
            NexusTransferScreen(balance, nexusTransferDetails(balance, "cd".repeat(32))!!, DEFAULT_ICON_URI, scroll,
                { "05 Nov. 2026, 18:13" }, {}, {}, {})
        }
    }
}
