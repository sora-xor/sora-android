/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.base.model

import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.toolbar.Action

enum class ToolbarType {

    CENTER_ALIGNED,
    SMALL,
    MEDIUM,
    LARGE
}

data class ToolbarState(
    val type: ToolbarType = ToolbarType.CENTER_ALIGNED,
    val title: String = "",
    val navIcon: Int = R.drawable.ic_toolbar_back,
    val action: String? = null,
    val menuActions: List<Action>? = null
)
