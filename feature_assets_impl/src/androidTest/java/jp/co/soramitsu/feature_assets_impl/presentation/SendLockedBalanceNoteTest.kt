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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.presentation.compose.components.NetworkFeeUnavailableNotice
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.AppStoreListing
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * at2-endpoints-balances: the Send form names the XOR that is locked on chain, and its fee
 * notice asks for an app update when the network needs one.
 */
@RunWith(AndroidJUnit4::class)
class SendLockedBalanceNoteTest {

    @get:Rule
    val rule = createComposeRule()

    private fun tagEndsWith(suffix: String) = SemanticsMatcher("test tag ends with $suffix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith(suffix) == true
    }

    private fun show(
        lockedNote: String?,
        feeUnavailable: NetworkFeeUnavailable? = null,
    ) {
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
                        reviewEnabled = feeUnavailable == null,
                        shouldTransactionReminderInsufficientWarningBeShown = false,
                        feeUnavailable = feeUnavailable,
                        lockedNote = lockedNote,
                        readOnly = false,
                    )
                }
            }
        }
    }

    @Test
    fun lockedXorIsNamedUnderTheAmount() {
        val note = "3.5 XOR is locked on the network, for example by staking or voting, " +
            "and can't be sent."
        show(lockedNote = note)

        rule.onNode(tagEndsWith("SendLockedBalanceNote"))
            .assertIsDisplayed()
            .assertTextEquals(note)
    }

    @Test
    fun noNoteWithoutALock() {
        show(lockedNote = null)

        rule.onNode(tagEndsWith("SendLockedBalanceNote")).assertDoesNotExist()
    }

    @Test
    fun aNetworkThatNeedsAnAppUpdateSaysSoInTheFeeNotice() {
        show(lockedNote = null, feeUnavailable = NetworkFeeUnavailable.APP_UPDATE_REQUIRED)

        rule.onNode(tagEndsWith("NetworkFeeUnavailable")).assertIsDisplayed()
        rule.onNode(
            SemanticsMatcher("says to update the app") { node ->
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.any { it.text.contains("update the app") } == true
            }
        ).assertIsDisplayed()
        // The text asks for an update, so the button is Update. Try again gave the same
        // notice every time and is not offered here.
        rule.onNode(tagEndsWith("NetworkFeeUpdateApp")).assertIsDisplayed()
        rule.onNode(tagEndsWith("NetworkFeeTryAgain")).assertDoesNotExist()
    }

    @Test
    fun aNetworkThatCannotBeReachedOffersTryAgainAndNoUpdate() {
        var retries = 0
        var updates = 0
        val shown = mutableStateOf(NetworkFeeUnavailable.NETWORK_UNREACHABLE)
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                NetworkFeeUnavailableNotice(
                    reason = shown.value,
                    onRetry = { retries++ },
                    onUpdateApp = { updates++ },
                )
            }
        }

        listOf(NetworkFeeUnavailable.NETWORK_UNREACHABLE, NetworkFeeUnavailable.OTHER)
            .forEachIndexed { index, reason ->
                shown.value = reason
                rule.waitForIdle()

                rule.onNode(tagEndsWith("NetworkFeeUpdateApp")).assertDoesNotExist()
                rule.onNode(tagEndsWith("NetworkFeeTryAgain")).assertIsDisplayed().performClick()
                assertEquals(index + 1, retries)
            }

        // The buttons follow the reason.
        shown.value = NetworkFeeUnavailable.APP_UPDATE_REQUIRED
        rule.waitForIdle()
        rule.onNode(tagEndsWith("NetworkFeeTryAgain")).assertDoesNotExist()
        rule.onNode(tagEndsWith("NetworkFeeUpdateApp")).assertIsDisplayed()
        assertEquals(0, updates)
    }

    @Test
    fun theUpdateButtonLeadsToTheUpdateAndNeverRetries() {
        var retries = 0
        var updates = 0
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                NetworkFeeUnavailableNotice(
                    reason = NetworkFeeUnavailable.APP_UPDATE_REQUIRED,
                    onRetry = { retries++ },
                    onUpdateApp = { updates++ },
                )
            }
        }

        rule.onNode(tagEndsWith("NetworkFeeUpdateApp")).assertIsDisplayed().performClick()

        assertEquals(1, updates)
        assertEquals(0, retries)
    }

    @Test
    fun theUpdateLeadsToThisAppsStorePage() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertEquals(
            "market://details?id=$packageName",
            AppStoreListing.storeUri(packageName).toString(),
        )
        assertEquals(
            "https://play.google.com/store/apps/details?id=$packageName",
            AppStoreListing.webUri(packageName).toString(),
        )
    }
}
