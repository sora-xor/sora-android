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
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
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
