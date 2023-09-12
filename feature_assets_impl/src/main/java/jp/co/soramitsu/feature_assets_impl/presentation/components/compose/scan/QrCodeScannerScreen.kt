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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.FilledButton
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
    val errorText: Text
        get() = throwable?.message?.let {
            Text.SimpleText(it)
        } ?: Text.StringRes(id = R.string.common_error_general_title)
}

@Composable
fun QrCodeScannerScreen(
    onNavIconClick: () -> Unit,
    onUploadFromGalleryClick: () -> Unit,
    onShowUserQrClick: () -> Unit
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

        val blurCameraBackground = remember { Color(color = 0x2818183D) }

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
        ) {
            SoramitsuToolbar(
                state = SoramitsuToolbarState(
                    basic = BasicToolbarState(
                        title = R.string.common_scan_qr,
                        navIcon = R.drawable.ic_close,
                    ),
                    type = SoramitsuToolbarType.SmallCentered()
                ),
                backgroundColor = blurCameraBackground,
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

                    onDrawBehind {
                        drawPath(
                            path = Path.combine(
                                operation = PathOperation.Difference,
                                path1 = wholeRect,
                                path2 = rectWithCorners
                            ),
                            color = blurCameraBackground
                        )
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

        BleachedButton(
            modifier = Modifier
                .constrainAs(
                    ref = showUserQrButton,
                    constrainBlock = {
                        start.linkTo(anchor = viewPort.start)
                        end.linkTo(anchor = viewPort.end)
                        bottom.linkTo(
                            anchor = parent.bottom,
                            margin = Dimens.x4
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
