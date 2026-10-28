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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun LoadableContentCard(
    modifier: Modifier,
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    innerPadding: PaddingValues,
    cornerRadius: Dp,
    state: ScreenStatus,
    onTryAgainClick: () -> Unit,
    contentWhenLoaded: @Composable () -> Unit,
) {
    ContentCard(
        modifier = modifier,
        innerPadding = innerPadding,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius
    ) {
        when (state) {
            ScreenStatus.LOADING -> {
                Box {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center),
                        color = MaterialTheme.customColors.fgPrimary
                    )
                }
            }
            ScreenStatus.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_error_80),
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
                        text = stringResource(id = R.string.common_error_general_title),
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
        state = ScreenStatus.LOADING,
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
        state = ScreenStatus.READY_TO_RENDER,
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
        state = ScreenStatus.ERROR,
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
