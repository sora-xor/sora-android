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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TextWithDelimiter(text1: String, text2: String, delimiter: String, color: Color, style: TextStyle) {
    Row {
        Text(
            modifier = Modifier
                .wrapContentSize(),
            text = text1,
            style = style,
            overflow = TextOverflow.Ellipsis,
            color = color,
            maxLines = 1,
        )
        Text(
            modifier = Modifier
                .wrapContentSize(),
            text = delimiter,
            style = style,
            overflow = TextOverflow.Ellipsis,
            color = color,
            maxLines = 1,
        )
        Text(
            modifier = Modifier
                .wrapContentSize(),
            text = text2,
            style = style,
            overflow = TextOverflow.Ellipsis,
            color = color,
            maxLines = 1,
        )
    }
}
