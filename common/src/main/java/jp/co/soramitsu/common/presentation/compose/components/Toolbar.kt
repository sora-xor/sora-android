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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import jp.co.soramitsu.common.presentation.compose.neon.neonChrome
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.PACKAGE_ID
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbar
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType

fun initNoToolbar() = SoramitsuToolbarState(
    basic = BasicToolbarState(
        title = "",
        visibility = false,
        navIcon = R.drawable.ic_arrow_left,
    ),
    type = SoramitsuToolbarType.SmallCentered(),
)

fun initSmallTitle2(title: Any) = SoramitsuToolbarState(
    basic = BasicToolbarState(
        title = title,
        visibility = true,
        navIcon = R.drawable.ic_arrow_left,
    ),
    type = SoramitsuToolbarType.Small(),
)

fun initMediumTitle2(title: Any) = SoramitsuToolbarState(
    basic = BasicToolbarState(
        title = title,
        visibility = true,
        navIcon = R.drawable.ic_arrow_left,
    ),
    type = SoramitsuToolbarType.Medium(),
)

fun initSmallTitleOnly(title: Any) = SoramitsuToolbarState(
    basic = BasicToolbarState(
        title = title,
        visibility = true,
        navIcon = null,
    ),
    type = SoramitsuToolbarType.Small(),
)

@Composable
fun Toolbar(
    toolbarState: SoramitsuToolbarState?,
    scrollState: ScrollState?,
    backgroundColor: Color,
    tintColor: Color,
    onNavClick: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    onMenuItemClick: ((Action) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
) {
    if (toolbarState != null && toolbarState.basic.visibility) {
        val threshold = with(LocalDensity.current) { 8.dp.toPx() }
        val scrolled = remember(scrollState, threshold) {
            derivedStateOf { (scrollState?.value ?: 0) > threshold }
        }
        val chrome by animateFloatAsState(
            targetValue = if (scrolled.value) 1f else 0f,
            animationSpec = tween(if (LocalFx.current.reduceMotion) 0 else 160),
            label = "toolbar chrome",
        )
        Box(Modifier.neonChrome(MaterialTheme.neon) { chrome }) {
            SoramitsuToolbar(
                state = toolbarState,
                elevation = 0.dp,
                backgroundColor = backgroundColor,
                tint = tintColor,
                onNavigate = onNavClick,
                onNavigateTestTag = "$PACKAGE_ID:id/OnNavigate",
                onAction = onActionClick,
                onActionTestTag = "$PACKAGE_ID:id/OnAction",
                onMenuItemClicked = onMenuItemClick,
                onMenuItemClickedTestTag = "$PACKAGE_ID:id/OnMenuItem",
                onSearch = onSearch,
                onSearchTestTag = "$PACKAGE_ID:id/OnSearch",
            )
        }
    }
}
