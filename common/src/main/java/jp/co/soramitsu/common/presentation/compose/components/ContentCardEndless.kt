/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.modifier.applyIf
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun ContentCardEndless(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = Dimens.x4,
                ambientColor = Color(0xFF999999),
                spotColor = Color(0xFF999999),
                shape = RoundedCornerShape(
                    topStart = MaterialTheme.borderRadius.xl,
                    topEnd = MaterialTheme.borderRadius.xl,
                ),
            )
            .clip(
                RoundedCornerShape(
                    topStart = MaterialTheme.borderRadius.xl,
                    topEnd = MaterialTheme.borderRadius.xl,
                )
            )
            .applyIf(onClick != null) {
                clickable { onClick?.invoke() }
            }
            .background(MaterialTheme.customColors.bgSurface)
            .padding(innerPadding),
        elevation = 0.dp,
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        ContentCardEndless(
            modifier = Modifier.padding(8.dp),
            innerPadding = PaddingValues(18.dp),
        ) {
            Text(
                text = "df\nfgfg",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
