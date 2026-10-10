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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeTags
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsBalanceCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendScreen
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
}
