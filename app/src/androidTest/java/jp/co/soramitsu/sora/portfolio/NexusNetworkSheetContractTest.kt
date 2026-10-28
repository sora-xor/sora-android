@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.portfolio

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_wallet_impl.data.nexus.*
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexusNetworkSheetContractTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owner = "FixtureOwnerAddressForDisplayOnly"
    private val peer = "FixturePeerAddressForDisplayOnly"
    private val hash = "ab".repeat(32)
    private fun balance() = NexusPortfolioBalance("fixture", WalletNetworkId.TAIRA, "Taira", true, owner,
        "12.5", TairaTestnetContract.XOR_ASSET_DEFINITION_ID, "https://example.invalid/explorer", true, emptyList(), emptyList(), null, null)
    private fun label(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performScrollTo().performClick()
    @Test fun historyRowsExposeExactCounterpartiesAndHashesAndRequestAnotherPage() {
        val transfers = listOf(NexusTransferHistoryItem(hash, 1_800_000_000_000, "2.5", owner, peer),
            NexusTransferHistoryItem("cd".repeat(32), 1_800_000_000_001, "3.75", peer, owner))
        val current = balance().copy(confirmedTransfers = transfers, historyHasMore = true,
            pendingTransactions = listOf(NexusPendingTransaction("pending", "ef".repeat(32), "xor", "1.25", peer, "PENDING", true)))
        val sheet = NetworkSheetsState().apply { openActivity(current) }; val events = mutableListOf<String>()
        compose.setContent { SoraAppTheme { CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
            override fun openUri(uri: String) { events += uri }
        }) { NetworkSheets(sheet, listOf(current), { true }, {}, {}, onLoadMore = { events += "more:${it.walletId}" }) } } }
        for (transfer in transfers) {
            val text = "${label(if (transfer.sender == owner) R.string.common_sent else R.string.common_received)} · ${transfer.amount} XOR"
            compose.onNodeWithText(text).performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.network_history_transaction_hash, transfer.transactionHash)).assertExists()
        }
        compose.onNodeWithText("1.25 XOR").assertExists()
        compose.onNodeWithText(label(R.string.wallet_pending_notice)).assertExists()
        click(R.string.nexus_history_load_more); click(R.string.wallet_network_details)
        compose.onNodeWithText(owner).assertExists()
        click(R.string.wallet_open_block_explorer)
        compose.runOnIdle { assertEquals(listOf("more:fixture", "https://example.invalid/explorer"), events) }
    }
    @Test fun receiveCopiesOnlyTheCurrentAddressAndReturnsToItsActivitySheet() {
        val current = balance(); val sheet = NetworkSheetsState().apply { openActivity(current) }
        compose.setContent { SoraAppTheme { NetworkSheets(sheet, listOf(current), { true }, {}, {}) } }
        click(R.string.common_receive)
        compose.onNodeWithText(owner).assertExists()
        compose.onNodeWithText(label(R.string.copy_receive_address)).performClick()
        compose.onNodeWithText(label(R.string.wallet_copied)).assertExists()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(owner, clipboard.primaryClip!!.getItemAt(0).text.toString())
            sheet.closeReceive()
        }
        compose.onNodeWithText(label(R.string.wallet_network_activity)).assertExists()
    }
    @Test fun changingTheWalletIdentityClosesTheOpenSheet() {
        val balances = mutableStateOf(listOf(balance()))
        val sheet = NetworkSheetsState().apply { openActivity(balance()) }
        compose.setContent { SoraAppTheme { NetworkSheets(sheet, balances.value, { true }, {}, {}) } }
        compose.onNodeWithText("12.5 XOR").assertExists()
        compose.runOnIdle { balances.value = listOf(balance().copy(xorQuantity = "15.5")) }
        compose.onNodeWithText("15.5 XOR").assertExists()
        compose.runOnIdle { balances.value = listOf(balance().copy(address = "replacement")) }
        compose.onNodeWithText("15.5 XOR").assertDoesNotExist()
        compose.runOnIdle { assertNull(sheet.selected) }
    }
    @Test fun productionAddressNeverOffersAnUnavailableSend() {
        val current = balance().copy(networkId = WalletNetworkId.MINAMOTO, networkName = "Minamoto", networkLive = false, sendAvailable = false, isTestnet = false)
        val sheet = NetworkSheetsState().apply { openActivity(current) }
        compose.setContent { SoraAppTheme { NetworkSheets(sheet, listOf(current), { true }, {}, {}) } }
        compose.onNodeWithText(label(R.string.common_send)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.nexus_production_not_live)).assertExists()
        compose.onNodeWithText(label(R.string.nexus_production_address_notice)).assertExists()
    }
    @Test fun liveNetworkWithDisabledSendingKeepsItsReceiveAndExplanationAvailable() {
        val current = balance().copy(sendAvailable = false)
        val sheet = NetworkSheetsState().apply { openActivity(current) }
        compose.setContent { SoraAppTheme { NetworkSheets(sheet, listOf(current), { true }, {}, {}) } }
        compose.onNodeWithText(label(R.string.nexus_send_not_available)).assertExists()
        compose.onNodeWithText(label(R.string.common_send)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.common_receive)).assertIsEnabled()
    }
}
