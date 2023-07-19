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

package jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

enum class HubCardVisibility {
    VISIBLE_AND_ENABLED,
    VISIBLE_AND_DISABLED,
    NOT_VISIBLE_ENABLED,
    NOT_VISIBLE_AND_DISABLED,
}

@JvmInline
value class HubCardState(
    val visibility: HubCardVisibility
) {

    val icon
        get() = when (visibility) {
            HubCardVisibility.VISIBLE_AND_ENABLED,
            HubCardVisibility.VISIBLE_AND_DISABLED -> R.drawable.ic_selected_accent_pin_24
            HubCardVisibility.NOT_VISIBLE_ENABLED,
            HubCardVisibility.NOT_VISIBLE_AND_DISABLED -> R.drawable.ic_selected_pin_empty_24
        }

    val alpha
        get() = when (visibility) {
            HubCardVisibility.VISIBLE_AND_ENABLED,
            HubCardVisibility.NOT_VISIBLE_ENABLED -> 1f
            HubCardVisibility.VISIBLE_AND_DISABLED,
            HubCardVisibility.NOT_VISIBLE_AND_DISABLED -> .3f
        }

    val isEnabled
        get() = when (visibility) {
            HubCardVisibility.VISIBLE_AND_ENABLED,
            HubCardVisibility.NOT_VISIBLE_ENABLED -> true
            HubCardVisibility.VISIBLE_AND_DISABLED,
            HubCardVisibility.NOT_VISIBLE_AND_DISABLED -> false
        }
}

data class EditCardsHubScreenState(
    val enabledCardsHeader: Text,
    val enabledCards: List<Pair<Text, HubCardState>>,
    val disabledCardsHeader: Text,
    val disabledCards: List<Pair<Text, HubCardState>>
)

@Composable
fun EditCardsHubScreen(
    state: EditCardsHubScreenState,
    onCardEnabled: (position: Int) -> Unit,
    onCardDisabled: (position: Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = Dimens.x2)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.x2)
    ) {
        if (state.enabledCards.isNotEmpty()) {
            ContentCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.x3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.x2)
                ) {
                    Text(
                        modifier = Modifier.padding(bottom = Dimens.x1_2),
                        text = state.enabledCardsHeader.retrieveString(),
                        style = MaterialTheme.customTypography.textS,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                    state.enabledCards.forEachIndexed { index, (name, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = state.isEnabled,
                                    onClick = { onCardEnabled.invoke(index) }
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.x1)
                        ) {
                            Image(
                                modifier = Modifier.size(size = Dimens.x3),
                                painter = painterResource(id = state.icon),
                                contentDescription = null,
                                alpha = state.alpha
                            )
                            Text(
                                text = name.retrieveString(),
                                style = MaterialTheme.customTypography.textM,
                                color = MaterialTheme.customColors.fgPrimary,
                            )
                        }
                    }
                }
            }
        }

        if (state.disabledCards.isNotEmpty()) {
            ContentCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.x3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.x2)
                ) {
                    Text(
                        modifier = Modifier.padding(bottom = Dimens.x1_2),
                        text = state.disabledCardsHeader.retrieveString(),
                        style = MaterialTheme.customTypography.textS,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                    state.disabledCards.forEachIndexed { index, (name, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = state.isEnabled,
                                    onClick = { onCardDisabled.invoke(index) }
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.x1)
                        ) {
                            Image(
                                modifier = Modifier.size(size = Dimens.x3),
                                painter = painterResource(id = state.icon),
                                contentDescription = null,
                                alpha = state.alpha
                            )
                            Text(
                                text = name.retrieveString(),
                                style = MaterialTheme.customTypography.textM,
                                color = MaterialTheme.customColors.fgPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewEditCardsHubScreen() {
    val enabledCards = remember {
        mutableStateOf<List<Pair<Text, HubCardState>>>(
            value = listOf(
                Text.SimpleText("Sora Card") to HubCardState(HubCardVisibility.VISIBLE_AND_ENABLED),
                Text.SimpleText("Buy Xor") to HubCardState(HubCardVisibility.VISIBLE_AND_ENABLED),
                Text.SimpleText("Assets") to HubCardState(HubCardVisibility.VISIBLE_AND_DISABLED),
                Text.SimpleText("Pools") to HubCardState(HubCardVisibility.VISIBLE_AND_ENABLED)
            )
        )
    }
    val disabledCards = remember {
        mutableStateOf<List<Pair<Text, HubCardState>>>(
            value = emptyList()
        )
    }
    EditCardsHubScreen(
        state = EditCardsHubScreenState(
            enabledCardsHeader = Text.SimpleText("Enabled"),
            enabledCards = enabledCards.value,
            disabledCardsHeader = Text.SimpleText("Disabled"),
            disabledCards = disabledCards.value
        ),
        onCardEnabled = { index ->
            enabledCards.value = enabledCards.value.run {
                mutableListOf<Pair<Text, HubCardState>>().apply {
                    addAll(this@run)
                    val (cardTitle, state) = get(index)
                    val newCardVisibility = when (state.visibility) {
                        HubCardVisibility.NOT_VISIBLE_ENABLED -> HubCardVisibility.VISIBLE_AND_ENABLED
                        else -> state.visibility
                    }
                    set(index, cardTitle to HubCardState(newCardVisibility))
                }
            }
        },
        onCardDisabled = { index ->
            disabledCards.value = disabledCards.value.run {
                mutableListOf<Pair<Text, HubCardState>>().apply {
                    addAll(this@run)
                    val (cardTitle, state) = get(index)
                    val newCardVisibility = when (state.visibility) {
                        HubCardVisibility.VISIBLE_AND_ENABLED -> HubCardVisibility.NOT_VISIBLE_ENABLED
                        else -> state.visibility
                    }
                    set(index, cardTitle to HubCardState(newCardVisibility))
                }
            }
        }
    )
}
