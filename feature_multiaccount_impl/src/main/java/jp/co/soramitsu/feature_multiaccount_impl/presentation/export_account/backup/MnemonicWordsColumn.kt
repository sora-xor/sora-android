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
