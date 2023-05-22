/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PercentContainer(
    modifier: Modifier,
    barVisible: Boolean,
    onSelectPercent: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val hideKeyboard: () -> Unit = {
            keyboardController?.hide()
        }
        val selectPercent: (Int) -> Unit = {
            keyboardController?.hide()
            onSelectPercent.invoke(it)
        }
        val keyboardVisible by keyboardState()
        content()
        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter),
            visible = keyboardVisible && barVisible,
        ) {
            PercentBar(
                onOptionSelect = selectPercent
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPercentContainer() {
    PercentContainer(
        modifier = Modifier.fillMaxSize(),
        barVisible = true,
        onSelectPercent = {},
        content = {
            Column {
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                TextField(value = "", onValueChange = {})
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
                Text(text = "text1")
            }
        },
    )
}
