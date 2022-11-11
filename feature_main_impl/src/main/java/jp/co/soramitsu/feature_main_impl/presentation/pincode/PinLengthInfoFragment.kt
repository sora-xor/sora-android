/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.presentation.compose.components.ContainedButton
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuBold15
import jp.co.soramitsu.common.presentation.compose.theme.neuBold34
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular15
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_impl.R

@AndroidEntryPoint
class PinLengthInfoFragment : BaseComposeFragment<BaseViewModel>() {

    override val viewModel: BaseViewModel = BaseViewModel()

    @Composable
    override fun Content(padding: PaddingValues) {
        Box(
            modifier = Modifier
                .background(ThemeColors.Background)
                .padding(bottom = Dimens.x5)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = Dimens.x3, end = Dimens.x3, bottom = Dimens.x7)
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (LocalConfiguration.current.screenHeightDp > 600) {
                    Image(
                        modifier = Modifier.padding(top = Dimens.x7),
                        painter = painterResource(id = R.drawable.ic_egyptians_closed),
                        contentDescription = null
                    )
                }
                Text(
                    text = stringResource(id = R.string.pin_update_info_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x9),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.neuBold34
                )
                Text(
                    text = stringResource(id = R.string.pin_update_info_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x5, start = Dimens.x2, end = Dimens.x2),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.neuRegular15
                )
                Text(
                    text = stringResource(id = R.string.pin_update_info_subtitle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x3, start = Dimens.x2, end = Dimens.x2),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.neuBold15
                )
            }

            ContainedButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Dimens.x2)
                    .fillMaxWidth(),
                label = stringResource(id = R.string.pin_update_button_text),
                onClick = {
                    requireActivity().onBackPressed()
                }
            )
        }
    }

    @Composable
    override fun Toolbar() {}
}
