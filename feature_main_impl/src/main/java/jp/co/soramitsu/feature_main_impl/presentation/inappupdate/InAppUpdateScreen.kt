/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.inappupdate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun InAppUpdateScreen(
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.customColors.bgPage)
            .padding(Dimens.x3)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = stringResource(id = R.string.common_update),
                style = MaterialTheme.customTypography.headline1,
                color = MaterialTheme.customColors.fgPrimary,
            )
            Text(
                modifier = Modifier
                    .padding(top = Dimens.x1)
                    .wrapContentSize()
                    .background(Color.Red, CircleShape)
                    .padding(Dimens.x1),
                text = stringResource(id = R.string.restart_required),
                style = MaterialTheme.customTypography.textM,
                color = Color.White,
            )
            Text(
                modifier = Modifier
                    .padding(top = Dimens.x1)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = stringResource(id = R.string.update_downloaded),
                style = MaterialTheme.customTypography.textS,
                color = MaterialTheme.customColors.fgPrimary,
            )
            Text(
                modifier = Modifier
                    .padding(top = Dimens.x1)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = stringResource(id = R.string.update_install_now),
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            FilledButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                enabled = true,
                size = Size.Large,
                order = Order.PRIMARY,
                text = stringResource(id = R.string.common_update),
                onClick = onUpdate,
            )
            Spacer(modifier = Modifier.size(Dimens.x2))
            FilledButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                enabled = true,
                size = Size.Large,
                order = Order.PRIMARY,
                text = stringResource(id = R.string.common_cancel),
                onClick = onCancel,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewInAppUpdateScreen() {
    InAppUpdateScreen(
        {},
        {},
    )
}
