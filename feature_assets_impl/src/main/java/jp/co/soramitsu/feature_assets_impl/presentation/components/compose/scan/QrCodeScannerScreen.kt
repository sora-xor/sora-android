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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbar
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import jp.co.soramitsu.ui_core.resources.Dimens

data class QRCodeScannerScreenState(
    val screenStatus: ScreenStatus,
    val throwable: Throwable?
) {
    val errorText: TextValue
        get() = throwable?.message?.let {
            TextValue.SimpleText(it)
        } ?: TextValue.StringRes(id = R.string.common_error_general_title)
}

@Composable
fun QrCodeScannerScreen(
    onNavIconClick: () -> Unit,
    onUploadFromGalleryClick: () -> Unit,
    onShowUserQrClick: () -> Unit,
    recipientOnly: Boolean = false,
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val (
            toolbar, leftSpacer, rightSpacer,
            bottomSpacer, viewPort,
            uploadFromGalleryButton, showUserQrButton
        ) = createRefs()

        // Design spec 7.19: the camera stays full-bleed under a #000 @ 55% scrim outside the
        // viewfinder, framed by cyan corner brackets.
        val blurCameraBackground = remember { Color.Black.copy(alpha = .55f) }
        val neon = MaterialTheme.neon
        val fx = LocalFx.current
        val bracketColor = neon.cyan
        val bracketGlow = fx.glows && fx.tier != FxTier.MINIMAL
        // Edge to edge: keep the bottom button above the navigation bar; the scrim still fills it.
        val navigationBarBottom =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier
                .constrainAs(
                    ref = toolbar,
                    constrainBlock = {
                        top.linkTo(anchor = parent.top)
                        start.linkTo(anchor = parent.start)
                        end.linkTo(anchor = parent.end)

                        height = Dimension.wrapContent
                        width = Dimension.fillToConstraints
                    }
                )
                // Edge to edge: keep the title and close button clear of the status bar.
                .background(blurCameraBackground)
                .statusBarsPadding()
        ) {
            SoramitsuToolbar(
                state = SoramitsuToolbarState(
                    basic = BasicToolbarState(
                        title = if (recipientOnly) R.string.wallet_scan_recipient else R.string.common_scan_qr,
                        navIcon = R.drawable.ic_close,
                    ),
                    type = SoramitsuToolbarType.SmallCentered()
                ),
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                onNavigate = onNavIconClick,
            )
        }

        Spacer(
            modifier = Modifier
                .constrainAs(
                    ref = leftSpacer,
                    constrainBlock = {
                        top.linkTo(anchor = toolbar.bottom)
                        start.linkTo(anchor = parent.start)
                        bottom.linkTo(anchor = bottomSpacer.top)

                        height = Dimension.fillToConstraints
                        width = Dimension.value(Dimens.x3)
                    }
                )
                .background(
                    color = blurCameraBackground
                )
        )

        Spacer(
            modifier = Modifier
                .constrainAs(
                    ref = viewPort,
                    constrainBlock = {
                        top.linkTo(anchor = toolbar.bottom)
                        start.linkTo(anchor = leftSpacer.end)
                        end.linkTo(anchor = rightSpacer.start)

                        width = Dimension.fillToConstraints
                    }
                )
                .aspectRatio(
                    ratio = 1f
                )
                .drawWithCache {
                    val wholeRect = Path().apply {
                        addRect(
                            rect = Rect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                            )
                        )
                    }

                    val rectWithCorners = Path().apply {
                        addRoundRect(
                            roundRect = RoundRect(
                                rect = Rect(
                                    left = 0f,
                                    top = 0f,
                                    right = size.width,
                                    bottom = size.height,
                                ),
                                cornerRadius = CornerRadius(
                                    x = Dimens.x4.toPx(),
                                    y = Dimens.x4.toPx()
                                )
                            )
                        )
                    }

                    val brackets = viewfinderBrackets(
                        width = size.width,
                        height = size.height,
                        radius = Dimens.x4.toPx(),
                        length = 24.dp.toPx(),
                    )
                    val bracketStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val glowStroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)

                    onDrawBehind {
                        drawPath(
                            path = Path.combine(
                                operation = PathOperation.Difference,
                                path1 = wholeRect,
                                path2 = rectWithCorners
                            ),
                            color = blurCameraBackground
                        )
                        if (bracketGlow) {
                            drawPath(brackets, bracketColor.copy(alpha = .25f), style = glowStroke)
                        }
                        drawPath(brackets, bracketColor, style = bracketStroke)
                    }
                }
        )

        Spacer(
            modifier = Modifier
                .constrainAs(
                    ref = rightSpacer,
                    constrainBlock = {
                        top.linkTo(anchor = toolbar.bottom)
                        end.linkTo(anchor = parent.end)
                        bottom.linkTo(anchor = bottomSpacer.top)

                        height = Dimension.fillToConstraints
                        width = Dimension.value(Dimens.x3)
                    }
                )
                .background(
                    color = blurCameraBackground
                )
        )

        Spacer(
            modifier = Modifier
                .constrainAs(
                    ref = bottomSpacer,
                    constrainBlock = {
                        top.linkTo(anchor = viewPort.bottom)
                        start.linkTo(anchor = parent.start)
                        end.linkTo(anchor = parent.end)
                        bottom.linkTo(anchor = parent.bottom)

                        height = Dimension.fillToConstraints
                        width = Dimension.fillToConstraints
                    }
                )
                .background(
                    color = blurCameraBackground
                )
        )

        FilledButton(
            modifier = Modifier
                .constrainAs(
                    ref = uploadFromGalleryButton,
                    constrainBlock = {
                        top.linkTo(
                            anchor = viewPort.bottom,
                            margin = Dimens.x3
                        )
                        start.linkTo(anchor = viewPort.start)
                        end.linkTo(anchor = viewPort.end)

                        width = Dimension.fillToConstraints
                    }
                ),
            size = Size.Large,
            order = Order.SECONDARY,
            text = stringResource(id = R.string.common_upload_from_library),
            onClick = onUploadFromGalleryClick
        )

        if (!recipientOnly) BleachedButton(
            modifier = Modifier
                .constrainAs(
                    ref = showUserQrButton,
                    constrainBlock = {
                        start.linkTo(anchor = viewPort.start)
                        end.linkTo(anchor = viewPort.end)
                        bottom.linkTo(
                            anchor = parent.bottom,
                            margin = Dimens.x4 + navigationBarBottom
                        )

                        width = Dimension.fillToConstraints
                    }
                ),
            size = Size.Large,
            order = Order.SECONDARY,
            text = stringResource(id = R.string.scan_qr_show_my_qr),
            onClick = onShowUserQrClick
        )
    }
}

/** Four L-brackets that follow the rounded corners of the viewfinder. */
private fun viewfinderBrackets(width: Float, height: Float, radius: Float, length: Float): Path {
    val r = radius
    val w = width
    val h = height
    return Path().apply {
        moveTo(0f, r + length)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(r + length, 0f)

        moveTo(w - r - length, 0f)
        lineTo(w - r, 0f)
        arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
        lineTo(w, r + length)

        moveTo(w, h - r - length)
        lineTo(w, h - r)
        arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
        lineTo(w - r - length, h)

        moveTo(r + length, h)
        lineTo(r, h)
        arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
        lineTo(0f, h - r - length)
    }
}

@Preview
@Composable
private fun PreviewQrCodeScannerScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(
                id = R.drawable.ic_close
            ),
            contentDescription = ""
        )
        QrCodeScannerScreen(
            onNavIconClick = {},
            onUploadFromGalleryClick = {},
            onShowUserQrClick = {}
        )
    }
}
