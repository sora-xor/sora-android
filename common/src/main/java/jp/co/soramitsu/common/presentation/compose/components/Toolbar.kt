/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
            onAction = onActionClick,
            onMenuItemClicked = onMenuItemClick,
        )
    }
}
