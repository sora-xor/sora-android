/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import jp.co.soramitsu.common.presentation.compose.resources.Dimens

@Composable
fun ProgressContainedButton(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    progress: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        ContainedButton(label = if (progress) "" else label, enabled = enabled && !progress, onClick = onClick)
        if (progress) {
            InfiniteProgressDots(
                Modifier
                    .height(Dimens.x2)
                    .fillMaxWidth(),
                Alignment.Center
            )
        }
    }
}
