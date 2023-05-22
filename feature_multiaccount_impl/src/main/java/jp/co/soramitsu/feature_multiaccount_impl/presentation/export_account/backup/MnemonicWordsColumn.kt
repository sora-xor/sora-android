/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.ui_core.resources.Dimens
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
                style = MaterialTheme.customTypography.textL
            )
        }
        val end = createEndBarrier(elements = refsIndex, margin = Dimens.x2)
        val refsWord = Array(words.size) { createRef() }
        words.forEachIndexed { index, s ->
            Text(
                modifier = Modifier.constrainAs(refsWord[index]) {
                    baseline.linkTo(refsIndex[index].baseline)
                    start.linkTo(end)
                },
                text = s,
                style = MaterialTheme.customTypography.textLBold
            )
        }
    }
}

@Preview(backgroundColor = 1874927, showBackground = true)
@Composable
private fun PreviewMnemonic() {
    MnemonicWordsColumn(
        startNumber = 95,
        words = List(12) { p -> "abcdefghijklmnop ${p + 111}" },
    )
}
