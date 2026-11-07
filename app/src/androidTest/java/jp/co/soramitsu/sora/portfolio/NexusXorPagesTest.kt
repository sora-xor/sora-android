@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.portfolio

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.util.PACKAGE_ID
import jp.co.soramitsu.common_wallet.presentation.compose.receive.ReceiveTokenByQrScreenState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.*
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * The XOR pages of SORA Nexus and Taira are the SORA2 asset, Receive, Send and transaction
 * pages: what each shows for a network, and where each tap goes.
 */
@RunWith(AndroidJUnit4::class)
class NexusXorPagesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private val owner = "testuFixtureOwnerAddressForLayoutOnly"
    private val peer = "testuFixturePeerAddressForLayoutOnly"
    private val sentHash = "ab".repeat(32)
    private val receivedHash = "cd".repeat(32)
    private val pendingHash = "ef".repeat(32)
    private val nbsp = ' '
    private val review = "$PACKAGE_ID:id/ReviewButton"

    private fun taira() = NexusPortfolioBalance(
        walletId = "fixture-wallet", networkId = WalletNetworkId.TAIRA, networkName = "Taira Testnet", isTestnet = true,
        address = owner, xorQuantity = "25000", assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
        explorerBaseUrl = "https://example.invalid/explorer", sendAvailable = true,
        pendingTransactions = emptyList(), confirmedTransfers = emptyList(), historyErrorCode = null, errorCode = null,
    )
    private fun withHistory() = taira().copy(
        confirmedTransfers = listOf(
            NexusTransferHistoryItem(sentHash, 1_800_000_000_000, "2.5", owner, peer),
            NexusTransferHistoryItem(receivedHash, 1_800_000_000_001, "3.75", peer, owner),
        ),
        pendingTransactions = listOf(NexusPendingTransaction("pending", pendingHash, "xor", "1.25", peer, "SUBMITTED", false, 1_800_000_000_002)),
        historyHasMore = true,
    )
    private fun production() = taira().copy(
        networkId = WalletNetworkId.MINAMOTO, networkName = "Minamoto", isTestnet = false, networkLive = false,
        sendAvailable = false, xorQuantity = null, address = "sorauFixtureProductionAddress",
    )
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun node(id: Int) = compose.onNodeWithText(text(id))
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) { content() } }
    }
    private fun showAsset(balance: State<NexusPortfolioBalance>) = show {
        NexusAssetScreen(
            balance = balance.value, xorIcon = null, xorName = "SORA", scrollState = rememberScrollState(),
            onRefresh = { events += "refresh" }, onSend = { events += "send" }, onReceive = { events += "receive" },
            onLoadMore = { events += "more" }, onTransferClick = { events += "transfer:$it" },
            onAddressClick = { events += "address" }, onOpenExplorer = { events += "explorer" },
            onRecovery = { events += "recovery" },
        )
    }

    @Before fun normal() { WalletRecoveryCapabilityGate.enterNormal() }
    @After fun cleanup() { WalletRecoveryCapabilityGate.enterNormal() }

    @Test fun tairaXorIsTheSora2AssetPageWithTheNetworkInPlaceOfThePrice() {
        showAsset(mutableStateOf(withHistory()))
        for (shown in listOf("XOR", "SORA", "Taira · Testnet", text(R.string.asset_details_liquid_balance),
            "25${nbsp}000 XOR", text(R.string.asset_details_recent_activity), text(R.string.account_address), owner)) {
            compose.onAllNodesWithText(shown).onFirst().assertExists()
        }
        // Polkaswap does not run there: no Swap. Fiat has no price on a testnet.
        node(R.string.polkaswap_swap_title).assertDoesNotExist()
        node(R.string.wallet_pending_notice).performScrollTo().assertExists()
        node(R.string.common_send).performScrollTo().performClick()
        node(R.string.common_receive).performScrollTo().performClick()
        compose.onNodeWithText("+3.75 XOR").performScrollTo().performClick()
        compose.onNodeWithText("2.5 XOR").performScrollTo().performClick()
        compose.onNodeWithText("1.25 XOR").performScrollTo().performClick()
        node(R.string.show_more).performScrollTo().performClick()
        // The address itself: the card around it also holds the explorer link.
        compose.onNodeWithText(owner, useUnmergedTree = true).performScrollTo().performClick()
        node(R.string.wallet_open_block_explorer).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("send", "receive", "transfer:$receivedHash", "transfer:$sentHash", "transfer:$pendingHash",
                "more", "address", "explorer"), events)
        }
    }

    @Test fun pullingThePageDownRefreshesIt() {
        showAsset(mutableStateOf(taira()))
        compose.onNodeWithText("SORA").performTouchInput { swipeDown(startY = top, endY = bottom + 600f) }
        compose.runOnIdle { assertTrue("refresh" in events) }
    }

    @Test fun anEmptyHistoryLeavesOutRecentActivityAsSora2Does() {
        showAsset(mutableStateOf(taira()))
        node(R.string.asset_details_recent_activity).assertDoesNotExist()
        node(R.string.show_more).assertDoesNotExist()
        node(R.string.common_send).assertExists()
    }

    @Test fun nothingToSendOrSendingOffLeavesOutSendAndSaysWhy() {
        val current = mutableStateOf(taira().copy(xorQuantity = "0"))
        showAsset(current)
        node(R.string.common_send).assertDoesNotExist()
        compose.runOnIdle { current.value = taira().copy(sendAvailable = false) }
        node(R.string.common_send).assertDoesNotExist()
        node(R.string.nexus_send_not_available).performScrollTo().assertExists()
        node(R.string.common_receive).assertIsEnabled()
    }

    @Test fun aReadOnlyAccountKeepsSendOff() {
        readOnly.value = true
        showAsset(mutableStateOf(taira()))
        node(R.string.common_send).performScrollTo().assertIsNotEnabled()
        node(R.string.common_receive).assertIsEnabled()
    }

    @Test fun theProductionNetworkBeforeLaunchOnlyOffersItsAddress() {
        showAsset(mutableStateOf(production()))
        compose.onNodeWithText("SORA Nexus · ${text(R.string.network_tag_coming_soon)}").assertExists()
        node(R.string.network_address_ready).assertExists()
        node(R.string.nexus_production_address_notice).performScrollTo().assertExists()
        node(R.string.common_send).assertDoesNotExist()
        node(R.string.asset_details_recent_activity).assertDoesNotExist()
        node(R.string.wallet_open_block_explorer).assertDoesNotExist()
        compose.onAllNodesWithText(text(R.string.nexus_production_address)).assertCountEquals(2)
        compose.onAllNodesWithText(text(R.string.nexus_production_address)).onFirst().performClick()
        compose.runOnIdle { assertEquals(listOf("receive"), events) }
    }

    @Test fun readFailuresAndRecoveryUseTheWalletMessagesInTheNoteStyle() {
        val current = mutableStateOf(taira().copy(xorQuantity = null, errorCode = "NEXUS_BALANCE_UNAVAILABLE",
            sendAvailable = false, historyErrorCode = "NEXUS_HISTORY_UNAVAILABLE"))
        showAsset(current)
        node(R.string.balance_unavailable).assertExists()
        // The wallet's own wording for each code, as everywhere else it reports one.
        node(R.string.wallet_issue_balance).assertExists()
        node(R.string.wallet_issue_unavailable).performScrollTo().assertExists()
        compose.onAllNodesWithText(text(R.string.wallet_check_again)).assertCountEquals(2)
        compose.onAllNodesWithText(text(R.string.wallet_check_again)).onFirst().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("refresh"), events) }
        // A balance still being read, then an earlier transfer still being checked.
        compose.runOnIdle {
            current.value = taira().copy(xorQuantity = null, errorCode = "NEXUS_PENDING_RECOVERY_REQUIRED", sendAvailable = false,
                recoveryPendingTransactions = listOf(NexusPendingTransaction("x", "aa", "other", "1", peer, "SUBMITTED", true)))
        }
        compose.onAllNodesWithText(text(R.string.safe_mode_pending_check)).assertCountEquals(2)
        compose.onAllNodesWithText(text(R.string.wallet_backup_options)).onFirst().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("refresh", "recovery"), events) }
        compose.runOnIdle { current.value = taira().copy(xorQuantity = null) }
        compose.onNodeWithText("…").assertExists()
    }

    @Test fun aTransferOpensOnTheSora2TransactionPage() {
        val details = nexusTransferDetails(withHistory(), receivedHash)!!
        show {
            NexusTransferScreen(withHistory(), details, null, rememberScrollState(), { "date:$it" },
                { events += "copy:$it" }, { events += "explorer" }, { events += "close" })
        }
        for (shown in listOf(text(R.string.common_received), "+3.75 XOR", text(R.string.status_successful), "date:1800000000001",
            receivedHash, text(R.string.common_recipient), owner, text(R.string.common_sender), peer)) {
            compose.onAllNodesWithText(shown).onFirst().assertExists()
        }
        node(R.string.block_id).assertDoesNotExist()
        compose.onNodeWithText(receivedHash).performScrollTo().performClick()
        node(R.string.wallet_open_block_explorer).performScrollTo().performClick()
        node(R.string.common_close).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy:$receivedHash", "explorer", "close"), events) }
    }

    @Test fun aTransferStillBeingCheckedSaysSoOnItsPage() {
        val details = nexusTransferDetails(withHistory(), pendingHash)!!
        show {
            NexusTransferScreen(withHistory(), details, null, rememberScrollState(), { "date:$it" }, {}, {}, {})
        }
        compose.onAllNodesWithText(text(R.string.common_sent)).onFirst().assertExists()
        node(R.string.status_pending).assertExists()
        node(R.string.wallet_transfer_pending).assertExists()
        compose.onNodeWithText("date:1800000000002").assertExists()
    }

    @Test fun receiveIsTheSora2ReceivePageWithTheNetworksAddressAndNotice() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val avatar = BitmapDrawable(compose.activity.resources, bitmap)
        val state = mutableStateOf(ReceiveTokenByQrScreenState(ScreenStatus.LOADING, null, null, null, null))
        val network = mutableStateOf(taira())
        show {
            NexusReceiveScreen(network.value, state.value, rememberScrollState(),
                { events += "copy" }, { events += "share" }, { events += "retry" })
        }
        node(R.string.common_share).assertIsNotEnabled()
        compose.runOnIdle { state.value = ReceiveTokenByQrScreenState(ScreenStatus.READY_TO_RENDER, bitmap, "Orbit", avatar, owner) }
        compose.onNodeWithText("Orbit").assertExists()
        compose.onNodeWithText(text(R.string.wallet_receive_notice, "Taira Testnet")).assertExists()
        node(R.string.commom_scan_qr).assertDoesNotExist()
        compose.onNodeWithText(owner).performClick()
        node(R.string.common_share).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy", "share"), events) }
        compose.runOnIdle { network.value = production() }
        node(R.string.nexus_production_address_notice).assertExists()
    }

    private fun showSend(state: State<NexusSendUiState>) = show {
        NexusSendScreen(
            state = state.value, xorIcon = null, xorName = "SORA", scrollState = rememberScrollState(),
            recipientAvatar = { null }, dateTime = { "date" },
            onRecipient = { events += "recipient:$it" }, onAmount = { events += "amount:$it" },
            onScan = { events += "scan" }, onReview = { events += "review" }, onConfirm = { events += "confirm" },
            onClose = { events += "close" }, onCopy = { events += "copy:$it" }, onOpenExplorer = { events += "explorer:$it" },
            onRecovery = { events += "recovery" },
        )
    }

    private fun prepared(): NexusPreparedSend {
        val network = taira()
        val account = jp.co.soramitsu.core_db.model.NetworkAccountLocal("fixture-wallet", "taira", "00", network.address, "", 1, true)
        val request = NexusTransferSigningRequest(NexusNetworks.taira, account, peer, "xor", "12.5")
        val quote = NexusTransferFeeQuote("taira", network.address, peer, "xor", "12.5", "0.01", "fixture", 123)
        return NexusPreparedSend(NexusSendRequest("fixture-wallet", WalletNetworkId.TAIRA, peer, "12.5"),
            peer, "12.5", "xor", "0.01", "25000", "fixture", request, quote)
    }

    @Test fun sendIsTheSora2SendFormWithTheRecipientTypedOrScanned() {
        val state = mutableStateOf(NexusSendUiState(visible = true, network = taira()))
        showSend(state)
        compose.onAllNodesWithText(text(R.string.recipient_address)).onFirst().assertExists()
        compose.onNodeWithText(text(R.string.wallet_send_network_notice, "Taira Testnet")).assertExists()
        compose.onNodeWithText(text(R.string.wallet_available_xor, "25${nbsp}000")).assertExists()
        compose.onNodeWithText(text(R.string.network_fee), substring = true).assertExists()
        compose.onNodeWithTag(review, useUnmergedTree = true).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(peer)
        compose.runOnIdle { assertTrue("recipient:$peer" in events) }
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("12.5")
        compose.waitUntil(5_000) { events.any { it == "amount:12.5" } }
        compose.runOnIdle { state.value = state.value.copy(recipient = peer, amount = "12.5") }
        compose.onNodeWithTag(review, useUnmergedTree = true).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue("review" in events) }
        // A scanned address replaces the typed one; a refused camera says what to do instead.
        compose.runOnIdle { state.value = state.value.copy(recipient = "scanned-address", scannerPermissionDenied = true) }
        compose.onNodeWithText("scanned-address").assertExists()
        node(R.string.wallet_scan_unavailable).assertExists()
        // A quote that failed: the message, and Check again prepares it again.
        compose.runOnIdle { state.value = state.value.copy(errorCode = "NEXUS_INVALID_ADDRESS") }
        node(R.string.wallet_issue_address).performScrollTo().assertExists()
        node(R.string.wallet_check_again).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, events.count { it == "review" }) }
        // An earlier transfer still being checked: no Review until it is known.
        compose.runOnIdle { state.value = state.value.copy(errorCode = "NEXUS_PENDING_RECOVERY_REQUIRED") }
        node(R.string.safe_mode_pending_check).performScrollTo().assertExists()
        compose.onNodeWithTag(review, useUnmergedTree = true).assertIsNotEnabled()
        node(R.string.wallet_backup_options).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("recovery", events.last()) }
    }

    @Test fun anAmountAboveTheBalanceIsMarkedAndKeepsReviewOff() {
        val state = mutableStateOf(NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "25000.5"))
        showSend(state)
        // SORA2's own hint and rule for an amount the account does not have.
        node(R.string.amount_error_no_funds).assertExists()
        compose.onNodeWithTag(review, useUnmergedTree = true).assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(amount = "25000") }
        node(R.string.amount_error_no_funds).assertDoesNotExist()
        compose.onNodeWithTag(review, useUnmergedTree = true).assertIsEnabled()
    }

    @Test fun theScanIconOfTheRecipientFieldOpensTheScanner() {
        showSend(mutableStateOf(NexusSendUiState(visible = true, network = taira())))
        // The field's trailing icon, as on SORA2's recipient screen.
        compose.onNode(hasClickAction() and !hasSetTextAction() and hasAnyAncestor(hasTestTag("$PACKAGE_ID:id/NexusRecipientInput")),
            useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("scan"), events) }
    }

    @Test fun theReviewIsTheSora2ConfirmationWithTheExactTransfer() {
        val state = mutableStateOf(NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "12.5", prepared = prepared()))
        showSend(state)
        compose.onNodeWithText(peer).assertExists()
        compose.onNodeWithText("Taira · Testnet").assertExists()
        compose.onNodeWithText("0.01 XOR").assertExists()
        compose.onNodeWithText(text(R.string.wallet_total_xor, "12.51")).assertExists()
        compose.onNodeWithText(text(R.string.wallet_available_xor, "25${nbsp}000")).assertExists()
        node(R.string.wallet_review_network).assertExists()
        compose.onNodeWithText(peer).performClick()
        node(R.string.common_confirm).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy:$peer", "confirm"), events) }
        compose.runOnIdle { state.value = state.value.copy(submitting = true) }
        node(R.string.common_confirm).assertDoesNotExist()
    }

    @Test fun theOutcomeIsTheSora2TransactionPage() {
        val state = mutableStateOf(NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "12.5",
            prepared = prepared(), result = NexusSendResult("local", sentHash, "CONFIRMED")))
        showSend(state)
        for (shown in listOf(text(R.string.common_sent), "12.5 XOR", text(R.string.status_successful), sentHash, "date",
            text(R.string.wallet_transfer_complete), peer, owner)) {
            compose.onAllNodesWithText(shown).onFirst().assertExists()
        }
        node(R.string.wallet_open_block_explorer).performScrollTo().performClick()
        node(R.string.common_close).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("explorer:https://example.invalid/explorer", "close"), events) }
        // The network did not say how it ended: no hash, and Activity is to be checked first.
        compose.runOnIdle {
            state.value = NexusSendUiState(visible = true, network = taira(), recipient = peer, amount = "12.5",
                errorCode = "transport lost", submissionNeedsCheck = true)
        }
        node(R.string.wallet_transaction_status_unknown).assertExists()
        node(R.string.wallet_submission_uncertain).assertExists()
        node(R.string.transaction_id).assertDoesNotExist()
        compose.onNodeWithText("12.5 XOR").assertExists()
    }
}
