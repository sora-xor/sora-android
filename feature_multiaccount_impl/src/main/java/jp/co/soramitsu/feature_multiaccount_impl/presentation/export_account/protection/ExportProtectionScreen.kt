/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ExportProtection(
    state: ExportProtectionScreenState,
    onItemClicked: (Int) -> Unit,
    continueClicked: () -> Unit
) {
    ContentCard(
        modifier = Modifier.padding(horizontal = Dimens.x2, vertical = Dimens.x1)
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.x3)
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = Dimens.x3),
                text = stringResource(id = state.descriptionResource),
                style = MaterialTheme.customTypography.paragraphM,
                color = MaterialTheme.customColors.fgPrimary,
            )

            ExportProtectionItems(
                state,
                onItemClicked
            )

            FilledButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x3),
                size = Size.Large,
                order = Order.PRIMARY,
                enabled = state.isButtonEnabled,
                text = stringResource(id = R.string.common_continue).uppercase(),
                onClick = continueClicked,
            )
        }
    }
}

@Composable
fun ExportProtectionItems(
    state: ExportProtectionScreenState,
    itemClicked: (index: Int) -> Unit
) {
    state.selectableItemList.forEachIndexed { index, it ->
        val shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
        Row(
            modifier = Modifier
                .padding(vertical = Dimens.x1_2)
                .border(
                    border = BorderStroke(
                        1.dp,
                        if (it.isSelected) MaterialTheme.customColors.accentPrimary else MaterialTheme.customColors.bgSurfaceVariant
                    ),
                    shape = shape,
                )
                .clip(shape)
                .clickable { itemClicked(index) }
                .padding(vertical = Dimens.x1, horizontal = Dimens.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier.size(Dimens.x3),
                painter = painterResource(id = if (it.isSelected) R.drawable.ic_selected_accent_pin_24 else R.drawable.ic_selected_pin_empty_24),
                contentDescription = null
            )

            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.x2),
                text = stringResource(id = it.textString)
            )
        }
    }
}

@Preview
@Composable
private fun ExportProtectionScreenPreview() {
    SoraAppTheme {
        ExportProtection(
            state = ExportProtectionScreenState(
                titleResource = R.string.common_raw_seed,
                descriptionResource = R.string.export_protection_seed_description,
                listOf(
                    ExportProtectionSelectableModel(
                        true,
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
