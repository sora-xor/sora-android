/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
                modifier = Modifier.padding(Dimens.x3),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2),
                    text = stringResource(id = R.string.mnemonic_confirmation_select_word_number),
                    style = MaterialTheme.customTypography.paragraphM,
                )

                Text(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2),
                    text = mnemonicConfirmationState.currentWordIndex.toString(),
                    style = MaterialTheme.customTypography.paragraphXSBold.copy(
                        fontSize = 128.sp
                    ),
                )

                Text(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2),
                    text = stringResource(id = R.string.mnemonic_confirmation_select_word_2),
                    style = MaterialTheme.customTypography.paragraphM,
                )

                Row {
                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .padding(vertical = Dimens.x1_2),
                        painter = painterResource(R.drawable.ic_circle_unchecked),
                        tint = if (mnemonicConfirmationState.confirmationStep > 1) { MaterialTheme.customColors.accentPrimary } else { MaterialTheme.customColors.bgSurfaceVariant },
                        contentDescription = null
                    )

                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .padding(vertical = Dimens.x1_2),
                        painter = painterResource(R.drawable.ic_circle_unchecked),
                        tint = if (mnemonicConfirmationState.confirmationStep > 2) { MaterialTheme.customColors.accentPrimary } else { MaterialTheme.customColors.bgSurfaceVariant },
                        contentDescription = null
                    )

                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .padding(vertical = Dimens.x1_2),
                        painter = painterResource(R.drawable.ic_circle_unchecked),
                        tint = if (mnemonicConfirmationState.confirmationStep > 3) { MaterialTheme.customColors.accentPrimary } else { MaterialTheme.customColors.bgSurfaceVariant },
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
        ),
        {}
    )
}
