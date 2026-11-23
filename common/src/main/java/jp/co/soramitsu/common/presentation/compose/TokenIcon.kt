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

package jp.co.soramitsu.common.presentation.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import jp.co.soramitsu.common.nexus.TairaTestnetContract
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R

/** Uses the immutable asset ID for the bundled XOR logo; other assets keep their catalogue artwork. */
@Composable
fun TokenBadge(assetId: String, symbol: String, uri: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (assetId == AssetHolder.XOR_ASSET_ID || assetId == TairaTestnetContract.XOR_ASSET_DEFINITION_ID) {
            Image(painterResource(R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000),
                contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            val characters = if (size >= 36.dp) 6 else 4
            val mark = if (symbol.length <= characters) symbol else symbol.take(characters - 2) + "…" + symbol.last()
            val markSize = with(LocalDensity.current) {
                (size * minOf(.42f, 1.28f / mark.length.coerceAtLeast(1))).toSp()
            }
            val monogram: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.neon.glassSolid), contentAlignment = Alignment.Center) {
                    Text(mark, modifier = Modifier.clearAndSetSemantics { }, color = MaterialTheme.neon.cyan, fontSize = markSize,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            if (uri.isNullOrBlank() || uri == DEFAULT_ICON_URI) {
                monogram()
            } else {
                SubcomposeAsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    loading = { monogram() }, error = { monogram() })
            }
        }
    }
}

@Composable
fun TokenIcon(
    modifier: Modifier = Modifier,
    uri: String?,
    size: Dp,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(uri ?: R.drawable.ic_token_default).build(),
        modifier = modifier
            .size(size = size),
        contentDescription = null,
        imageLoader = LocalContext.current.imageLoader,
    )
}

@Composable
fun AccountIcon(
    modifier: Modifier = Modifier,
    drawable: Drawable?,
    size: Dp,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(drawable ?: R.drawable.ic_token_default).build(),
        modifier = modifier
            .size(size = size),
        contentDescription = null,
        imageLoader = LocalContext.current.imageLoader,
    )
}
