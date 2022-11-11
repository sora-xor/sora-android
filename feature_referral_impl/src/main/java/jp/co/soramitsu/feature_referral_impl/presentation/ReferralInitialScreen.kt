/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.NeuColorsCompat
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuBold18
import jp.co.soramitsu.common.presentation.compose.theme.neuMedium15
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular12
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular16
import jp.co.soramitsu.common.presentation.compose.theme.neuSemiBold11
import jp.co.soramitsu.feature_referral_impl.R

@Composable
fun SheetContentReferralCommon(
    text: String,
    content: @Composable (ColumnScope) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(
                start = Dimens.x3,
                top = Dimens.x1,
                end = Dimens.x3,
                bottom = Dimens.x1
            )
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 64.dp, height = 4.dp)
                .align(Alignment.CenterHorizontally)
                .background(
                    color = NeuColorsCompat.neuDividerColor,
                    shape = RoundedCornerShape(size = 2.dp)
                )
        )
        Text(
            text = text.toUpperCase(Locale.current),
            modifier = Modifier
                .padding(top = 12.dp, bottom = Dimens.x2)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = neuSemiBold11,
            color = NeuColorsCompat.neuOnBackground
        )
        content.invoke(this)
    }
}

@Composable
fun SheetContentReferrer(
    referrer: String,
) {
    Text(
        text = stringResource(id = R.string.referral_referrer_description),
        style = neuMedium15,
        color = NeuColorsCompat.color281818
    )
    Text(
        text = stringResource(id = R.string.referral_referrer_address),
        modifier = Modifier.padding(top = Dimens.x3, bottom = Dimens.x05),
        color = NeuColorsCompat.color9a9a9a,
        style = neuRegular12
    )
    Text(
        text = referrer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = Color.Black,
        style = neuRegular16
    )
}

@Composable
fun ReferralStartScreen(
    state: ReferralProgramState,
    onFirstClick: () -> Unit,
    onSecondClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.Background)
            .padding(bottom = Dimens.x4)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeColors.Background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier
                    .padding(
                        top = Dimens.x3,
                        start = Dimens.x3,
                        end = Dimens.x3
                    )
                    .fillMaxWidth(),
                text = stringResource(id = R.string.referral_title),
                textAlign = TextAlign.Start,
                style = neuBold18
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Text(
                    modifier = Modifier
                        .padding(
                            top = Dimens.x2,
                            start = Dimens.x3,
                            end = Dimens.x3
                        )
                        .fillMaxWidth(),
                    text = stringResource(id = R.string.referral_subtitle),
                    textAlign = TextAlign.Start,
                    style = neuMedium15
                )

                if (LocalConfiguration.current.screenHeightDp > 600) {
                    Image(
                        painterResource(R.drawable.image_friends),
                        contentDescription = stringResource(id = R.string.referral_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = Dimens.x4, start = Dimens.x4)
                            .graphicsLayer { alpha = 0.99f }
                            .drawWithContent {
                                val colors = listOf(
                                    Color.Black,
                                    Color.Transparent
                                )
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors,
                                        0f,
                                        400f
                                    ),
                                    blendMode = BlendMode.DstOut
                                )
                            }
                    )
                }
            }
        }
        ReferralButtons(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimens.x3),
            onTopButtonClick = { onFirstClick.invoke() },
            onBottomButtonClick = { onSecondClick.invoke() },
            topButtonText = R.string.referral_start_inviting,
            bottomButtonText = if (state.common.referrer == null) R.string.referral_enter_link_title else R.string.referral_your_referrer
        )
    }
}

@Preview
@Composable
private fun SheetContentReferrerPreview() {
    SheetContentReferrer(referrer = "referrer address")
}

@Preview
@Composable
private fun ReferralStartScreenPreview() {
    ReferralStartScreen(
        state = ReferralProgramState(
            common = ReferrerState(
                referrer = "address",
                activate = false,
                referrerFee = "0.003 XOR",
                extrinsicFee = "0.234 XOR"
            ),
            screen = ReferralProgramStateScreen.Initial,
            bondState = ReferralBondState(
                invitationsCount = 2,
                invitationsAmount = "0.9 XOR",
                balance = "12 XOR"
            )
        ),
        onFirstClick = {},
        onSecondClick = {},
    )
}
