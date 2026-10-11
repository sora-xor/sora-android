/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.feature_assets_impl.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendScreen
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
    }
}
