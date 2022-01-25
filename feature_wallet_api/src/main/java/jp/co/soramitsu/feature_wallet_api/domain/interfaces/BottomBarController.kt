/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

interface BottomBarController {

    fun showBottomBar()

    fun hideBottomBar()

    fun navigateTabToSwap()
}
