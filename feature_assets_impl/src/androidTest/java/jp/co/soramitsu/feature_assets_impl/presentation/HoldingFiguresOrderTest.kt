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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    /**
     * Every character of the text is laid out, none behind an ellipsis, and each line fits the text's place on screen.
     * The place is the node's measured size: the layout a text node hands to a test can be made again at the width it
     * was offered, wider than the text took, so its own overflow flags say nothing about what is drawn.
     */
    private fun SemanticsNodeInteraction.assertNotCut() {
        val node = fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val text = layout.layoutInput.text
        val lines = layout.lineCount
        val seen = "\"$text\" in a ${node.size} place, $lines lines, " +
            "widest ${(0 until lines).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }} px, " +
            "bottom ${layout.getLineBottom(lines - 1)} px"
        assertTrue("never cut with an ellipsis: $seen", (0 until lines).none(layout::isLineEllipsized))
        assertTrue("every character is laid out: $seen", layout.getLineEnd(lines - 1, visibleEnd = true) >= text.trimEnd().length)
        assertTrue(
            "no line is wider than its place: $seen",
            (0 until lines).all { layout.getLineRight(it) - layout.getLineLeft(it) <= node.size.width + 1f },
        )
        assertTrue("no line falls below its place: $seen", layout.getLineBottom(lines - 1) <= node.size.height + 1f)
        // Nor is any line cut by what holds the text: each line's ink, placed in the text's place as its alignment puts
        // it, lies within what shows of that place on screen (clipped by every parent), from the first line's top to
        // the last line's baseline.
        val origin = node.positionInRoot
        val shown = node.boundsInRoot
        val align = layout.layoutInput.style.textAlign
        for (line in 0 until lines) {
            val width = layout.getLineRight(line) - layout.getLineLeft(line)
            val left = origin.x + when (align) {
                TextAlign.End, TextAlign.Right -> node.size.width - width
                TextAlign.Center -> (node.size.width - width) / 2f
                else -> layout.getLineLeft(line)
            }
            assertTrue(
                "line $line shows whole across, $left..${left + width} in ${shown.left}..${shown.right}: $seen",
                left >= shown.left - CLIP_SLACK && left + width <= shown.right + CLIP_SLACK,
            )
        }
        assertTrue(
            "the first line's top shows, ${origin.y + layout.getLineTop(0)} under ${shown.top}: $seen",
            origin.y + layout.getLineTop(0) >= shown.top - CLIP_SLACK,
        )
        assertTrue(
            "the last line shows down to its baseline, ${origin.y + layout.getLineBaseline(lines - 1)} over ${shown.bottom}: $seen",
            origin.y + layout.getLineBaseline(lines - 1) <= shown.bottom + CLIP_SLACK,
        )
    }

    /** [content] 320 dp wide, with its text at [fontScale] times its size. */
    private fun narrow(fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Column(Modifier.width(320.dp)) { content() }
                }
            }
        }
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

    @Test
    fun aLongAmountOnANarrowScreenIsNeverCutAndTheNameKeepsItsSymbol() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column(Modifier.width(320.dp)) {
                    AssetItem(
                        assetState = holding("PSWAP", LONG_AMOUNT, "$1\u00A0234\u00A0567.89", "-3.1 %").copy(tokenName = "Polkaswap"),
                        testTag = "LongRow",
                        onClick = {},
                    )
                }
            }
        }
        text(LONG_AMOUNT).assertIsDisplayed().assertNotCut()
        val symbol = text("PSWAP").assertIsDisplayed()
        val bounds = symbol.getUnclippedBoundsInRoot()
        assertTrue("the name keeps its symbol", (bounds.right - bounds.left).value > 0f)
        symbol.assertNotCut()
    }

    @Test
    fun aLongAmountOnANarrowScreenWithTheLargestTextIsNeverCut() {
        narrow(fontScale = 2f) {
            AssetItem(
                assetState = holding("PSWAP", LONG_AMOUNT, "$1\u00A0234\u00A0567.89", "-3.1 %").copy(tokenName = "Polkaswap"),
                testTag = "LongRow",
                onClick = {},
            )
        }
        text(LONG_AMOUNT).assertIsDisplayed().assertNotCut()
        text("PSWAP").assertIsDisplayed().assertNotCut()
        text("$1\u00A0234\u00A0567.89").assertIsDisplayed().assertNotCut()
        assertAmountOverValue(text(LONG_AMOUNT), text("$1\u00A0234\u00A0567.89"))
    }

    @Test
    fun aLongFrozenAmountBesideALongLabelIsNeverCutWithTheLargestTextOnANarrowScreen() {
        // Turkish "Dondurulmuş" in the 18 sp headline, twice its size, 320 dp wide: the amount goes under the label.
        narrow(fontScale = 2f) {
            val context = LocalContext.current
            val turkish = android.content.res.Configuration(LocalConfiguration.current).apply {
                setLocale(java.util.Locale.forLanguageTag("tr"))
            }
            val inTurkish = context.createConfigurationContext(turkish)
            CompositionLocalProvider(LocalContext provides inTurkish, LocalResources provides inTurkish.resources) {
                AssetDetailsBalanceCard(
                    amount = "0 XOR",
                    amountFiat = "$0",
                    frozenAmount = LONG_AMOUNT,
                    frozenAmountFiat = "$11",
                    onSendClick = {},
                    onReceiveClick = {},
                    onSwapClick = {},
                    onBalanceClick = null,
                    readOnly = false,
                )
            }
        }
        rule.onNodeWithText("Dondurulmuş", useUnmergedTree = true).assertIsDisplayed()
        val frozen = rule.onNode(tagEndsWith("AssetFrozenAmount"), useUnmergedTree = true)
        frozen.assertIsDisplayed().assertNotCut()
        assertAmountOverValue(frozen, rule.onNode(tagEndsWith("AssetFrozenAmountFiat"), useUnmergedTree = true))
    }

    @Test
    fun longPooledAmountsOnANarrowScreenWithTheLargestTextAreNeverCut() {
        narrow(fontScale = 2f) {
            PoolsList(
                PoolsListState(
                    listOf(
                        PoolsListItemState(
                            token1Icon = DEFAULT_ICON_URI,
                            token2Icon = DEFAULT_ICON_URI,
                            poolToken1Symbol = "XOR",
                            poolToken2Symbol = "PSWAP",
                            poolAmounts = "$LONG_AMOUNT - $LONG_AMOUNT",
                            fiat = "$99",
                            fiatChange = "",
                            tokenIds = "xor" to "pswap",
                            rewardTokenIconsList = emptyList(),
                        )
                    )
                )
            )
        }
        val whole = LONG_AMOUNT.replace(' ', '\u00A0')
        text("$whole - $whole").assertIsDisplayed().assertNotCut()
        text("$99").assertIsDisplayed().assertNotCut()
        assertAmountOverValue(text("$whole - $whole"), text("$99"))
    }

    @Test
    fun longPooledAmountsOnANarrowScreenAreNeverCut() {
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column(Modifier.width(320.dp)) {
                    PoolsList(
                        PoolsListState(
                            listOf(
                                PoolsListItemState(
                                    token1Icon = DEFAULT_ICON_URI,
                                    token2Icon = DEFAULT_ICON_URI,
                                    poolToken1Symbol = "XOR",
                                    poolToken2Symbol = "PSWAP",
                                    poolAmounts = "$LONG_AMOUNT - $LONG_AMOUNT",
                                    fiat = "$99",
                                    fiatChange = "",
                                    tokenIds = "xor" to "pswap",
                                    rewardTokenIconsList = emptyList(),
                                )
                            )
                        )
                    )
                }
            }
        }
        val whole = LONG_AMOUNT.replace(' ', '\u00A0')
        text("$whole - $whole").assertIsDisplayed().assertNotCut()
        text("$99").assertIsDisplayed()
    }

    private companion object {
        /** Half a pixel: what rounding to whole pixels can leave between a line and the edge that shows it. */
        const val CLIP_SLACK = 0.5f
        const val LONG_AMOUNT = "123\u00A0456\u00A0789.123456 PSWAP"
    }
}
