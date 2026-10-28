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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun MnemonicWordsColumn(
    startNumber: Int,
    words: List<String>,
) {
    ConstraintLayout(
        modifier = Modifier
            .wrapContentSize()
    ) {
        val refsIndex = Array(words.size) { createRef() }
        words.forEachIndexed { index, _ ->
            Text(
                modifier = Modifier.constrainAs(refsIndex[index]) {
                    top.linkTo(if (index == 0) parent.top else refsIndex[index - 1].bottom)
                    start.linkTo(parent.start)
                },
                text = (startNumber + index).toString(),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textL
            )
        }
        val margin = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
            Dimens.x2
        } else {
            -Dimens.x2
        }
        val barrier = createEndBarrier(elements = refsIndex, margin = margin)
        val refsWord = Array(words.size) { createRef() }
        words.forEachIndexed { index, s ->
            Text(
                modifier = Modifier.constrainAs(refsWord[index]) {
                    baseline.linkTo(refsIndex[index].baseline)
                    start.linkTo(barrier)
                },
                text = s,
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textLBold
            )
        }
    }
}

@Preview(backgroundColor = 1874927, showBackground = true, locale = "ru")
@Composable
private fun PreviewMnemonic() {
    val letters = "abcdefghijklmnop"
    val size = letters.length - 1
    val s = List(7) {
        val len = (3..8).random()
        buildString {
            repeat(len) {
                val p = (0..size).random()
                this.append(letters[p])
            }
        }
    }
    MnemonicWordsColumn(
        startNumber = 97,
        words = s,
    )
}

@Preview(backgroundColor = 1874927, showBackground = true, locale = "he")
@Composable
private fun PreviewMnemonicRtl() {
    val letters = "abcdefghijklmnop"
    val size = letters.length - 1
    val s = List(7) {
        val len = (3..8).random()
        buildString {
            repeat(len) {
                val p = (0..size).random()
                this.append(letters[p])
            }
        }
    }
    MnemonicWordsColumn(
        startNumber = 97,
        words = s,
    )
}
