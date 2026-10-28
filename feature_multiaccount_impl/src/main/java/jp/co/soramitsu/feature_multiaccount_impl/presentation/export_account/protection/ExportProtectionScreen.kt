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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.CheckboxButton
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ExportProtection(
    state: ExportProtectionScreenState,
    onItemClicked: (Int) -> Unit,
    continueClicked: () -> Unit
) {
    ContentCard(
        modifier = Modifier.padding(horizontal = Dimens.x2, vertical = Dimens.x1).fillMaxWidth().wrapContentHeight()
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
                text = state.descriptionText ?: stringResource(id = state.descriptionResource),
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
        CheckboxButton(
            isSelected = it.isSelected,
            itemClicked = { itemClicked(index) },
            text = stringResource(id = it.textString)
        )
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
