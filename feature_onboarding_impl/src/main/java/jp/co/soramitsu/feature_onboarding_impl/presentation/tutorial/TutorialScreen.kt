/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import jp.co.soramitsu.common.presentation.compose.components.ContainedButton
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeumorphButton
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuBold34
import jp.co.soramitsu.common.presentation.compose.theme.neuButton
import jp.co.soramitsu.common.presentation.compose.theme.neuLight15
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingViewModel

@ExperimentalUnitApi
@Composable
internal fun TutorialScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    navController: NavController
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.Background)
            .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x4)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = Dimens.x7 + Dimens.x7 + Dimens.x5, top = Dimens.x3),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val painter = painterResource(R.drawable.ic_sora_logo_big)
            Image(
                modifier = Modifier.weight(1f, fill = false)
                    .sizeIn(maxWidth = 168.dp, maxHeight = 168.dp)
                    .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
                alignment = Alignment.BottomCenter,
                painter = painter,
                contentDescription = null
            )

            Column(
                modifier = Modifier
                    .padding(top = Dimens.x4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.tutorial_many_world),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.neuBold34
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.x2),
                    text = stringResource(id = R.string.asset_sora_fullname),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.neuBold34,
                    color = ThemeColors.Primary
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(id = R.string.tutorial_many_world_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.neuLight15,
                    lineHeight = 21.sp
                )
            }
        }

        TutorialButtons(
            modifier = Modifier.align(Alignment.BottomCenter),
            onCreateAccount = {
                viewModel.onSignUpClicked(navController)
            },
            onRecoveryAccount = {
                viewModel.onRecoveryClicked(navController)
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TutorialButtons(
    modifier: Modifier = Modifier,
    onCreateAccount: () -> Unit,
    onRecoveryAccount: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedButton(
            label = stringResource(id = R.string.create_account_title).uppercase(),
            onClick = onCreateAccount
        )

        NeumorphButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(id = R.string.recovery_title),
            textStyle = MaterialTheme.typography.neuButton,
            onClick = onRecoveryAccount
        )
    }
}
