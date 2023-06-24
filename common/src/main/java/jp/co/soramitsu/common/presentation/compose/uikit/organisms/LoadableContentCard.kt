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

package jp.co.soramitsu.common.presentation.compose.uikit.organisms

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrievePainter
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

data class LoadableContentCardState(
    val screenStatus: ScreenStatus
) {
    val isLoading: Boolean = screenStatus === ScreenStatus.LOADING

    val isErrorCaught: Boolean = screenStatus === ScreenStatus.ERROR

    val errorImage: Image = Image.ResImage(
        id = R.drawable.ic_error_80
    )

    val errorText: Text = Text.StringRes(
        id = R.string.common_error_general_title
    )
}

@Composable
fun LoadableContentCard(
    modifier: Modifier,
    innerPadding: PaddingValues,
    cornerRadius: Dp,
    state: LoadableContentCardState,
    onTryAgainClick: () -> Unit,
    contentWhenLoaded: @Composable () -> Unit,
) {
    ContentCard(
        modifier = modifier,
        innerPadding = innerPadding,
        cornerRadius = cornerRadius
    ) {
        when {
            state.isLoading -> {
                Box {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center),
                        color = MaterialTheme.customColors.fgPrimary
                    )
                }
            }
            state.isErrorCaught -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = state.errorImage.retrievePainter(),
                        contentDescription = null
                    )
                    Divider(
                        thickness = Dimens.x1,
                        color = Color.Transparent
                    )
                    Text(
                        modifier = Modifier.clickable {
                            onTryAgainClick.invoke()
                        },
                        text = state.errorText.retrieveString(),
                    )
                }
            }
            else -> {
                contentWhenLoaded.invoke()
            }
        }
    }
}

@Preview
@Composable
private fun PreviewLoadableContentCard_LOADING() {
    LoadableContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        innerPadding = PaddingValues(
            all = Dimens.x3
        ),
        state = LoadableContentCardState(
            screenStatus = ScreenStatus.LOADING
        ),
        cornerRadius = Dimens.x4,
        contentWhenLoaded = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "Loaded"
                )
            }
        },
        onTryAgainClick = { }
    )
}

@Preview
@Composable
private fun PreviewLoadableContentCard_READY_TO_RENDER() {
    LoadableContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        innerPadding = PaddingValues(
            all = Dimens.x3
        ),
        cornerRadius = Dimens.x4,
        state = LoadableContentCardState(
            screenStatus = ScreenStatus.READY_TO_RENDER
        ),
        contentWhenLoaded = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "Loaded"
                )
            }
        },
        onTryAgainClick = { }
    )
}

@Preview
@Composable
private fun PreviewLoadableContentCard_ERROR() {
    LoadableContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        innerPadding = PaddingValues(
            all = Dimens.x3
        ),
        state = LoadableContentCardState(
            screenStatus = ScreenStatus.ERROR
        ),
        cornerRadius = Dimens.x4,
        contentWhenLoaded = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "Loaded"
                )
            }
        },
        onTryAgainClick = { }
    )
}
