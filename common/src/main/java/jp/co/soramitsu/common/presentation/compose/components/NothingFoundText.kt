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

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.ProvideShimmer
import jp.co.soramitsu.common.presentation.compose.neon.skeleton
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.ui_core.lit.litRise
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun NothingFoundText(text: String = stringResource(R.string.common_nothing_available)) {
    val colors = MaterialTheme.neon
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.litRise(LocalFx.current.litMotion).size(48.dp)
            .litPad(CircleShape, elevation = 4.dp).padding(12.dp)) {
            val stroke = 1.8.dp.toPx()
            drawRoundRect(colors.cyan, Offset(stroke, size.height * .18f),
                Size(size.width - 2 * stroke, size.height * .64f), CornerRadius(3.dp.toPx()), style = Stroke(stroke))
            drawLine(colors.cyan, Offset(size.width * .29f, center.y),
                Offset(size.width * .71f, center.y), stroke, StrokeCap.Round)
        }
        Text(text, style = MaterialTheme.customTypography.textSBold, color = colors.textSecondary,
            textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingRows(description: String, modifier: Modifier = Modifier, count: Int = 3) {
    ProvideShimmer {
        Column(modifier.fillMaxWidth().progressSemantics().semantics { contentDescription = description }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(count) { index ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).skeleton(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth(if (index % 2 == 0) .42f else .58f).height(10.dp).skeleton(CircleShape))
                        Box(Modifier.fillMaxWidth(.27f).height(7.dp).skeleton(CircleShape))
                    }
                    Box(Modifier.width(48.dp).height(12.dp).skeleton(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}
