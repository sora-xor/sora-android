/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_impl.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SelectedAccountSigningHost
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsBalanceCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** U9 (design spec 9.2): in read-only mode Send and Swap are disabled and say why. */
@RunWith(AndroidJUnit4::class)
class ReadOnlySendTest {

    @get:Rule
    val rule = createComposeRule()

    @After
    fun tearDown() {
        WalletRecoveryCapabilityGate.enterNormal()
    }

    private companion object {
        const val SELECTED = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
    }

    private fun tagEndsWith(suffix: String) = SemanticsMatcher("test tag ends with $suffix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith(suffix) == true
    }

    @Test
    fun readOnlyModeDisablesSendAndSwapOnTheAssetScreen() {
        var sends = 0
        WalletRecoveryCapabilityGate.enterBlocked()
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                AssetDetailsBalanceCard(
                    amount = "10 XOR",
                    amountFiat = "$1",
                    isTransferableAmountAvailable = true,
                    onSendClick = { sends++ },
                    onReceiveClick = {},
                    onSwapClick = {},
                    onBalanceClick = {},
                )
            }
        }
        rule.onNode(tagEndsWith("SendButton")).assertIsNotEnabled()
        rule.onNode(tagEndsWith("SwapButton")).assertIsNotEnabled()
        rule.onNode(tagEndsWith("ReceiveButton")).assertIsEnabled()
        rule.onNodeWithTag(SafeModeTags.READ_ONLY_REASON).assertExists()

        // Leaving read-only mode turns them back on (the screen follows the gate).
        WalletRecoveryCapabilityGate.enterNormal()
        rule.waitForIdle()
        rule.onNode(tagEndsWith("SendButton")).assertIsEnabled().performClick()
        assertEquals(1, sends)
        rule.onNodeWithTag(SafeModeTags.READ_ONLY_REASON).assertDoesNotExist()
    }

    @Test
    fun readOnlyModeDisablesReviewOnTheSendForm() {
        WalletRecoveryCapabilityGate.enterInspectedRecovery()
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column {
                    SendScreen(
                        address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
                        icon = null,
                        onAddressClick = {},
                        onAddressLongClick = {},
                        inputState = previewAssetAmountInputState,
                        onAmountChange = {},
                        onSelectToken = {},
                        onFocusChange = {},
                        onReviewClick = {},
                        feeAmount = "0.007 XOR",
                        feeFiat = "$0.01",
                        feeLoading = false,
                        reviewEnabled = true,
                        shouldTransactionReminderInsufficientWarningBeShown = false,
                    )
                }
            }
        }
        rule.onNode(tagEndsWith("ReviewButton")).assertIsNotEnabled()
        rule.onNodeWithTag(SafeModeTags.READ_ONLY_REASON).assertExists()
    }

    // ---- An account that cannot sign on this phone (the rest of the app works) -----------------

    @Composable
    private fun SendForm(onReview: () -> Unit) {
        SendScreen(
            address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
            icon = null,
            onAddressClick = {},
            onAddressLongClick = {},
            inputState = previewAssetAmountInputState,
            onAmountChange = {},
            onSelectToken = {},
            onFocusChange = {},
            onReviewClick = onReview,
            feeAmount = "0.007 XOR",
            feeFiat = "$0.01",
            feeLoading = false,
            reviewEnabled = true,
            shouldTransactionReminderInsufficientWarningBeShown = false,
        )
    }

    @Test
    fun aReadOnlyAccountIsExplainedAboveTheSendFormWithItsRepairAndReviewIsOff() {
        // The production host of every screen (SoraBaseFragment sets exactly this as its
        // content), fed by a stand-in for the repository's flow: when the selected account's
        // stored key cannot be used, the notice is above the form and the form's button is off.
        val repairs = mutableListOf<String>()
        var reviews = 0
        val signing = MutableStateFlow(SelectedAccountSigning(SELECTED, readOnly = true))
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SelectedAccountSigningHost(
                    source = { signing },
                    explainsReadOnlyAccount = true,
                    onRepair = { repairs += it },
                ) {
                    SendForm(onReview = { reviews++ })
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Said before anything is filled in, with the reason and the way to repair it.
        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertIsDisplayed()
        rule.onNodeWithText(
            context.getString(jp.co.soramitsu.common.R.string.account_read_only_title)
        ).assertIsDisplayed()
        rule.onNodeWithTag(AccountReadOnlyTags.REASON).assertIsDisplayed()
        rule.onNodeWithTag(AccountReadOnlyTags.REPAIR).performClick()
        // The repair opens Account options of the selected account.
        assertEquals(listOf(SELECTED), repairs)

        // The form cannot be sent from, and says why next to its button.
        rule.onNode(tagEndsWith("ReviewButton")).assertIsNotEnabled()
        rule.onNodeWithTag(AccountReadOnlyTags.BUTTON_REASON).assertExists()
        // The whole app is not in read-only mode: that reason is not shown.
        rule.onNodeWithTag(SafeModeTags.READ_ONLY_REASON).assertDoesNotExist()

        // Once its keys are restored, Send works again without leaving the screen.
        signing.value = SelectedAccountSigning(SELECTED, readOnly = false)
        rule.waitForIdle()
        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
        rule.onNodeWithTag(AccountReadOnlyTags.BUTTON_REASON).assertDoesNotExist()
        rule.onNode(tagEndsWith("ReviewButton")).assertIsEnabled().performClick()
        assertEquals(1, reviews)
    }

    @Test
    fun inReadOnlyModeOfTheWholeAppTheAccountNoticeIsNotShown() {
        // After an interrupted upgrade check no account was judged. The host says nothing about
        // the account then; the form shows the app-wide reason and stays off.
        WalletRecoveryCapabilityGate.enterInspectedRecovery()
        val signing = MutableStateFlow(SelectedAccountSigning(SELECTED, readOnly = true))
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SelectedAccountSigningHost(
                    source = { signing },
                    explainsReadOnlyAccount = true,
                    onRepair = {},
                ) {
                    SendForm(onReview = {})
                }
            }
        }

        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
        rule.onNodeWithTag(AccountReadOnlyTags.BUTTON_REASON).assertDoesNotExist()
        rule.onNode(tagEndsWith("ReviewButton")).assertIsNotEnabled()
        rule.onNodeWithTag(SafeModeTags.READ_ONLY_REASON).assertExists()

        // The mode ends (Try upgrade again passed): the account's own notice appears.
        WalletRecoveryCapabilityGate.enterNormal()
        rule.waitForIdle()
        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertIsDisplayed()
    }

    @Test
    fun aScreenThatDoesNotSignGetsNoNoticeButStillKnowsTheAccountIsReadOnly() {
        val signing = MutableStateFlow(SelectedAccountSigning(SELECTED, readOnly = true))
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                SelectedAccountSigningHost(
                    source = { signing },
                    explainsReadOnlyAccount = false,
                    onRepair = {},
                ) {
                    SendForm(onReview = {})
                }
            }
        }

        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
        rule.onNode(tagEndsWith("ReviewButton")).assertIsNotEnabled()
    }

    @Test
    fun anAccountThatSignsSeesNoNoticeAndCanReview() {
        var reviews = 0
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column { SendForm(onReview = { reviews++ }) }
            }
        }

        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
        rule.onNodeWithTag(AccountReadOnlyTags.BUTTON_REASON).assertDoesNotExist()
        rule.onNode(tagEndsWith("ReviewButton")).assertIsEnabled().performClick()
        assertEquals(1, reviews)
    }
}
