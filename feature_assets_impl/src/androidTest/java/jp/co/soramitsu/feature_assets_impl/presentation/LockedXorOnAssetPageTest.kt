/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_assets_impl.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItem
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsBalanceCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * at2-endpoints-balances: an account whose XOR is all locked has nothing transferable. The asset
 * page and the home row must still show that the XOR is there, how much, and why it cannot be
 * sent; otherwise the correct "0 XOR" looks like lost funds.
 */
@RunWith(AndroidJUnit4::class)
class LockedXorOnAssetPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun tagEndsWith(suffix: String) = SemanticsMatcher("test tag ends with $suffix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith(suffix) == true
    }

    private val note = "110 XOR is locked on the network, for example by staking or voting, " +
        "and can\u0027t be sent."

    @Test
    fun allLockedXorShowsTheFrozenAmountAndTheReasonAndOpensTheBreakdown() {
        var breakdownOpened = 0
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                AssetDetailsBalanceCard(
                    amount = "0 XOR",
                    amountFiat = "$0",
                    frozenAmount = "110 XOR",
                    frozenAmountFiat = "$11",
                    lockedNote = note,
                    isTransferableAmountAvailable = false,
                    onSendClick = {},
                    onReceiveClick = {},
                    onSwapClick = {},
                    onBalanceClick = { breakdownOpened++ },
                    readOnly = false,
                )
            }
        }

        // The card is one clickable element, so its texts are read from the unmerged tree.
        rule.onNode(tagEndsWith("AssetFrozenAmount"), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("110 XOR")
        rule.onNode(tagEndsWith("AssetLockedNote"), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(note)
        // Nothing can be sent, so there is no Send button; Receive stays.
        rule.onNode(tagEndsWith("SendButton")).assertDoesNotExist()
        rule.onNode(tagEndsWith("ReceiveButton")).assertIsEnabled()

        rule.onNode(tagEndsWith("AssetFrozenAmount"), useUnmergedTree = true).performClick()
        assertEquals(1, breakdownOpened)
    }

    @Test
    fun noNoteWithoutALock() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                AssetDetailsBalanceCard(
                    amount = "12 XOR",
                    amountFiat = "$1.2",
                    frozenAmount = "0 XOR",
                    frozenAmountFiat = "$0",
                    isTransferableAmountAvailable = true,
                    onSendClick = {},
                    onReceiveClick = {},
                    onSwapClick = {},
                    onBalanceClick = {},
                    readOnly = false,
                )
            }
        }

        rule.onNode(tagEndsWith("AssetLockedNote"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(tagEndsWith("AssetFrozenAmount"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(tagEndsWith("SendButton")).assertIsEnabled()
    }

    @Test
    fun theHomeRowNamesTheFrozenAmountNextToTheLiquidOne() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column {
                    AssetItem(
                        assetState = AssetItemCardState(
                            tokenIcon = DEFAULT_ICON_URI,
                            tokenId = "0x0200000000000000000000000000000000000000000000000000000000000000",
                            tokenName = "SORA",
                            tokenSymbol = "XOR",
                            assetAmount = "0 XOR",
                            assetFiatAmount = "$0",
                            fiatChange = "+1%",
                            frozenAmount = "110 XOR",
                        ),
                        testTag = "LockedRow",
                        onClick = {},
                    )
                    AssetItem(
                        assetState = AssetItemCardState(
                            tokenIcon = DEFAULT_ICON_URI,
                            tokenId = "0x0200040000000000000000000000000000000000000000000000000000000000",
                            tokenName = "SORA Validator Token",
                            tokenSymbol = "VAL",
                            assetAmount = "5 VAL",
                            assetFiatAmount = "$1",
                            fiatChange = "+1%",
                        ),
                        testTag = "LiquidRow",
                        onClick = {},
                    )
                }
            }
        }

        rule.onNodeWithText("0 XOR \u00b7 110 XOR frozen").assertIsDisplayed()
        // A row without frozen funds reads as it always did.
        rule.onNodeWithText("5 VAL ").assertIsDisplayed()
    }
}
