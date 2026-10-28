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
import jp.co.soramitsu.common.presentation.compose.neon.ProtectWalletSecrets
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MnemonicConfirmationState
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.SafeContentCard as ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun MnemonicConfirmationScreen(
    mnemonicConfirmationState: MnemonicConfirmationState,
    onButtonClicked: (String) -> Unit
) {
    ProtectWalletSecrets()
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
