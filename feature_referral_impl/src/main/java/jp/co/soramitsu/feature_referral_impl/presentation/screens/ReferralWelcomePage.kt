/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralBondState
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralButtons
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralCommonState
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralInvitationsCardState
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralProgramState
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralsCardState
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ReferralWelcomePageScreen(
    state: ReferralProgramState,
    onStartInviting: () -> Unit,
    onEnterLink: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .padding(top = Dimens.x1_5)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        ConstraintLayout(
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            val titleBias = createGuidelineFromStart(0.6f)
            val descriptionBias = createGuidelineFromStart(0.5f)
            val bannerStartBias = createGuidelineFromStart(0.4f)
            val bannerTopBias = createGuidelineFromTop(0.76f)
            val (banner, title, description, buttons) = createRefs()

            Image(
                painter = painterResource(R.drawable.image_friends),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                modifier = Modifier
                    .constrainAs(banner) {
                        start.linkTo(bannerStartBias)
                        bottom.linkTo(bannerTopBias)
                        top.linkTo(parent.top)
                        end.linkTo(parent.end)

                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        val colors = listOf(
                            Color.Black,
                            Color.Transparent
                        )
                        val whiteRectWidth = this.size.width * 0.1f
                        val gradientWhiteStartX = 125f
                        val gradientWhiteEndX = 250f
                        drawContent()
                        drawRect(
                            color = Color.Black,
                            size = this.size.copy(width = whiteRectWidth),
                            blendMode = BlendMode.DstOut
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors,
                                gradientWhiteStartX,
                                gradientWhiteEndX
                            ),
                            blendMode = BlendMode.DstOut
                        )
                    }
            )

            Text(
                modifier = Modifier
                    .padding(start = Dimens.x3, top = Dimens.x3)
                    .constrainAs(title) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                        end.linkTo(titleBias)
                        width = Dimension.fillToConstraints
                    },
                text = stringResource(id = R.string.referral_title),
                style = MaterialTheme.customTypography.headline2
            )

            Text(
                modifier = Modifier
                    .padding(start = Dimens.x3, top = Dimens.x2, bottom = Dimens.x2)
                    .constrainAs(description) {
                        start.linkTo(parent.start)
                        top.linkTo(title.bottom)
                        end.linkTo(descriptionBias)
                        width = Dimension.fillToConstraints
                    },
                text = stringResource(id = R.string.referral_subtitle),
                textAlign = TextAlign.Start,
                style = MaterialTheme.customTypography.paragraphM
            )

            ReferralButtons(
                modifier = Modifier
                    .padding(
                        start = Dimens.x3,
                        end = Dimens.x3,
                        top = Dimens.x1,
                        bottom = Dimens.x3
                    )
                    .constrainAs(buttons) {
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        top.linkTo(description.bottom)
                        width = Dimension.fillToConstraints
                    },
                onTopButtonClick = onStartInviting,
                onBottomButtonClick = onEnterLink,
                topButtonText = R.string.referral_start_inviting,
                bottomButtonText = if (state.common.referrer == null) R.string.referral_enter_link_title else R.string.referral_your_referrer
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWelcomePageScreen() {
    ReferralWelcomePageScreen(
        state = ReferralProgramState(
            common = ReferralCommonState(
                activate = true,
                referrer = "address",
                referrerFee = "0.003 XOR",
                extrinsicFee = "0.234 XOR",
                extrinsicFeeFiat = "$12"
            ),
            bondState = ReferralBondState(
                invitationsCount = 2, invitationsAmount = "0.9 XOR", balance = "12 XOR"
            ),
            referrerInputState = InputTextState(),
            referralInvitationsCardState = ReferralInvitationsCardState(
                "Available Invitations",
                5,
                WrappedTextState(title = "Invitations Link", text = "polkaswap.io/#/referral/cnVyaue39dssBc2bReZycusLdys3vbeoz2irRF8BbwVcdCNmm"),
                "0.007 XOR",
                referrals = ReferralsCardState()
            )
        ),
        onStartInviting = {}, onEnterLink = {}
    )
}
