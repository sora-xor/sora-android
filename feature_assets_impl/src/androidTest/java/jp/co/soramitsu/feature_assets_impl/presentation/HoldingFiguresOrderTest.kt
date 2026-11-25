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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsBalanceCard
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItem
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where one holding is shown as an amount and its value, the amount is the figure: above the value and in larger type.
 * The value is never the headline of a holding.
 */
@RunWith(AndroidJUnit4::class)
class HoldingFiguresOrderTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(value: String) = rule.onNodeWithText(value, useUnmergedTree = true)

    private fun tagEndsWith(suffix: String) = SemanticsMatcher("test tag ends with $suffix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith(suffix) == true
    }

    private fun SemanticsNodeInteraction.fontSize(): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.fontSize.value
    }

    private fun assertAmountOverValue(amount: SemanticsNodeInteraction, value: SemanticsNodeInteraction) {
        amount.assertIsDisplayed()
        value.assertIsDisplayed()
        val amountBounds = amount.getUnclippedBoundsInRoot()
        val valueBounds = value.getUnclippedBoundsInRoot()
        assertTrue("the amount is above its value", amountBounds.bottom <= valueBounds.top)
        assertTrue("the amount is set larger than its value", amount.fontSize() > value.fontSize())
    }

    private fun holding(symbol: String, amount: String, fiat: String, change: String) = AssetItemCardState(
        tokenIcon = DEFAULT_ICON_URI,
        tokenId = "0x0200040000000000000000000000000000000000000000000000000000000000",
        tokenName = "SORA Validator Token",
        tokenSymbol = symbol,
        assetAmount = amount,
        assetFiatAmount = fiat,
        fiatChange = change,
    )

    @Test
    fun anAssetRowShowsTheAmountOverItsValue() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                AssetItem(assetState = holding("VAL", "12.5 VAL", "$40.1", "+2.1 %"), testTag = "PricedRow", onClick = {})
            }
        }
        assertAmountOverValue(text("12.5 VAL"), text("$40.1"))
        text("+2.1 %").assertIsDisplayed()
    }

    @Test
    fun anAssetRowWithoutAPriceShowsTheAmountAlone() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                AssetItem(assetState = holding("ABC", "5 ABC", "", ""), testTag = "UnpricedRow", onClick = {})
            }
        }
        text("5 ABC").assertIsDisplayed()
        rule.onNodeWithText("—", substring = true, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("$", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aPricedHoldingWorthNothingShowsAZeroUnderTheAmount() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                AssetItem(assetState = holding("XOR", "0 XOR", "$0", ""), testTag = "ZeroRow", onClick = {})
            }
        }
        assertAmountOverValue(text("0 XOR"), text("$0"))
    }

    @Test
    fun aPoolRowShowsThePooledAmountsOverTheirValue() {
        val pool = PoolsListItemState(
            token1Icon = DEFAULT_ICON_URI,
            token2Icon = DEFAULT_ICON_URI,
            poolToken1Symbol = "XOR",
            poolToken2Symbol = "VAL",
            poolAmounts = "2 XOR - 5 VAL",
            fiat = "$12",
            fiatChange = "",
            tokenIds = "xor" to "val",
            rewardTokenIconsList = emptyList(),
        )
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column { PoolsList(PoolsListState(listOf(pool))) }
            }
        }
        assertAmountOverValue(text("2 XOR - 5 VAL"), text("$12"))
    }

    @Test
    fun theFrozenRowOnAnAssetPageShowsTheAmountOverItsValue() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                AssetDetailsBalanceCard(
                    amount = "0 XOR",
                    amountFiat = "$0",
                    frozenAmount = "110 XOR",
                    frozenAmountFiat = "$11",
                    onSendClick = {},
                    onReceiveClick = {},
                    onSwapClick = {},
                    onBalanceClick = null,
                    readOnly = false,
                )
            }
        }
        assertAmountOverValue(
            rule.onNode(tagEndsWith("AssetFrozenAmount"), useUnmergedTree = true),
            rule.onNode(tagEndsWith("AssetFrozenAmountFiat"), useUnmergedTree = true),
        )
    }
}
