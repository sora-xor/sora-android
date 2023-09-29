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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MnemonicConfirmationState
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun MnemonicConfirmationScreen(
    mnemonicConfirmationState: MnemonicConfirmationState,
    onButtonClicked: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = Dimens.x2)
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.x3, top = Dimens.x1),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.x3),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2),
                    text = stringResource(id = R.string.mnemonic_confirmation_select_word_number),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.paragraphM,
                )

                Text(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2),
                    text = mnemonicConfirmationState.currentWordIndex.toString(),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.paragraphXSBold.copy(
                        fontSize = 128.sp
                    ),
                )

                Text(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2),
                    text = stringResource(id = R.string.mnemonic_confirmation_select_word_2),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.paragraphM,
                )

                Row {
                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .padding(vertical = Dimens.x1_2),
                        painter = painterResource(R.drawable.ic_circle_unchecked),
                        tint = if (mnemonicConfirmationState.confirmationStep > 1) {
                            MaterialTheme.customColors.accentPrimary
                        } else {
                            MaterialTheme.customColors.bgSurfaceVariant
                        },
                        contentDescription = null
                    )

                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .padding(vertical = Dimens.x1_2),
                        painter = painterResource(R.drawable.ic_circle_unchecked),
                        tint = if (mnemonicConfirmationState.confirmationStep > 2) {
                            MaterialTheme.customColors.accentPrimary
                        } else {
                            MaterialTheme.customColors.bgSurfaceVariant
                        },
                        contentDescription = null
                    )

                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .padding(vertical = Dimens.x1_2),
                        painter = painterResource(R.drawable.ic_circle_unchecked),
                        tint = if (mnemonicConfirmationState.confirmationStep > 3) {
                            MaterialTheme.customColors.accentPrimary
                        } else {
                            MaterialTheme.customColors.bgSurfaceVariant
                        },
                        contentDescription = null
                    )
                }
            }
        }

        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.x1_5),
            text = mnemonicConfirmationState.buttonsList[0],
            onClick = { onButtonClicked(mnemonicConfirmationState.buttonsList[0]) },
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.x1_5),
            text = mnemonicConfirmationState.buttonsList[1],
            onClick = { onButtonClicked(mnemonicConfirmationState.buttonsList[1]) },
            size = Size.Large,
            order = Order.PRIMARY
        )

        BleachedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.x1_5),
            text = mnemonicConfirmationState.buttonsList[2],
            onClick = { onButtonClicked(mnemonicConfirmationState.buttonsList[2]) },
            size = Size.Large,
            order = Order.PRIMARY
        )
    }
}

@Composable
@Preview
fun PreviewMnemonicConfirmationScreen() {
    MnemonicConfirmationScreen(
        mnemonicConfirmationState = MnemonicConfirmationState(
            0,
            listOf("First", "Second", "Thrird"),
            0
        )
    ) {}
}
