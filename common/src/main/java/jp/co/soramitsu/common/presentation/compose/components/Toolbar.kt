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

import androidx.compose.foundation.ScrollState
import androidx.compose.material.AppBarDefaults
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
        val elevation = remember(scrollState) {
            derivedStateOf {
                if (scrollState == null || scrollState.value == 0) {
                    0.dp
                } else {
                    AppBarDefaults.TopAppBarElevation
                }
            }
        }

        SoramitsuToolbar(
            state = toolbarState,
            elevation = elevation.value,
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
