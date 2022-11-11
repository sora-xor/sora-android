/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.components.ContainedButton
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPressed
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ExportProtectionScreen(
    state: ExportProtectionScreenState,
    viewModel: ExportProtectionViewModel
) {
    ExportProtection(
        state,
        viewModel::onItemClicked,
        viewModel::continueClicked
    )
}

@Composable
fun ExportProtection(
    state: ExportProtectionScreenState,
    onItemClicked: (Int) -> Unit,
    continueClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.Start)
                .padding(horizontal = Dimens.x2),
            text = stringResource(id = state.descriptionResource),
            style = MaterialTheme.customTypography.paragraphM,
            color = MaterialTheme.customColors.fgPrimary
        )

        val screenHeight = LocalConfiguration.current.screenHeightDp

        if (screenHeight > 550) {
            val painter = painterResource(id = R.drawable.ic_lock_16)

            Icon(
                modifier = Modifier.weight(1f, fill = false)
                    .padding(top = Dimens.x4)
                    .sizeIn(maxWidth = 158.dp, maxHeight = 158.dp)
                    .fillMaxHeight()
                    .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
                painter = painter,
                tint = MaterialTheme.customColors.fgTetriary,
                contentDescription = null
            )
        }

        ExportProtectionItems(
            Modifier
                .padding(top = Dimens.x4, start = Dimens.x2, end = Dimens.x2),
            state,
            onItemClicked
        )

        ContainedButton(
            modifier = Modifier
                .padding(Dimens.x2)
                .fillMaxWidth(),
            enabled = state.isButtonEnabled,
            label = stringResource(id = R.string.transaction_continue).uppercase(),
            onClick = continueClicked
        )
    }
}

@Composable
fun ExportProtectionItems(modifier: Modifier, state: ExportProtectionScreenState, itemClicked: (index: Int) -> Unit) {
    Column(
        modifier = modifier,
    ) {
        state.selectableItemList.forEachIndexed { index, it ->
            NeuCardPressed(modifier = Modifier.padding(bottom = Dimens.x1)) {
                Row(
                    modifier = Modifier
                        .clickable { itemClicked(index) }
                        .padding(horizontal = Dimens.x2, vertical = Dimens.x1)
                ) {
                    Image(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        painter = painterResource(id = if (it.isSelected) R.drawable.ic_selected_accent_pin_24 else R.drawable.ic_selected_pin_empty_24),
                        contentDescription = null
                    )

                    Text(
                        modifier = Modifier.padding(start = Dimens.x2),
                        text = stringResource(id = it.textString)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ExportProtectionScreenPreview() {
    SoraAppTheme {
        ExportProtection(
            state = ExportProtectionScreenState(
                titleResource = R.string.common_raw_seed,
                descriptionResource = R.string.export_protection_seed_description,
                listOf(
                    ExportProtectionSelectableModel(
                        textString = R.string.export_protection_seed_1
                    ),
                    ExportProtectionSelectableModel(
                        textString = R.string.export_protection_seed_2
                    ),
                    ExportProtectionSelectableModel(
                        textString = R.string.export_protection_seed_3
                    )
                )
            ),
            { },
            { }
        )
    }
}
