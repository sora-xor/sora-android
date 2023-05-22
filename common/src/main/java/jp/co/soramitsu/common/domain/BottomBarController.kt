/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import androidx.annotation.AttrRes

interface BottomBarController {

    fun showBottomBar()

    fun hideBottomBar()

    fun isBottomBarVisible(): Boolean
}

interface BarsColorhandler {

    fun setColor(@AttrRes color: Int)
}
