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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItem
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A token on Polkaswap's blacklist that the account holds is listed on the full asset list only,
 * and its row says that Polkaswap does not list it. Tapping the row still opens its page.
 */
@RunWith(AndroidJUnit4::class)
class NotListedAssetRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun row(id: String, name: String, symbol: String, amount: String, notListed: Boolean) = AssetItemCardState(
        tokenIcon = DEFAULT_ICON_URI,
        tokenId = id,
        tokenName = name,
        tokenSymbol = symbol,
        assetAmount = amount,
        assetFiatAmount = "",
        fiatChange = "",
        notListed = notListed,
    )

    @Test
    fun theRowOfAHeldBlacklistedTokenSaysPolkaswapDoesNotListIt() {
        val opened = mutableListOf<String>()
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column {
                    AssetItem(
                        assetState = row("0x" + "bb".repeat(32), "Unlisted", "ABC", "5 ABC", notListed = true),
                        testTag = "NotListedRow",
                        onClick = { opened += it },
                    )
                    AssetItem(
                        assetState = row("0x0200040000000000000000000000000000000000000000000000000000000000", "SORA Validator Token", "VAL", "5 VAL", notListed = false),
                        testTag = "ListedRow",
                        onClick = {},
                    )
                }
            }
        }

        // The amount is the row's figure; the note is on the line under the name, after the symbol.
        rule.onNodeWithText("ABC · Not listed by Polkaswap").assertIsDisplayed()
        rule.onNodeWithText("5 ABC").assertIsDisplayed().performClick()
        assertEquals(listOf("0x" + "bb".repeat(32)), opened)
        // A listed token's row has the symbol alone under its name.
        rule.onNodeWithText("VAL").assertIsDisplayed()
        rule.onNodeWithText("5 VAL").assertIsDisplayed()
    }
}
