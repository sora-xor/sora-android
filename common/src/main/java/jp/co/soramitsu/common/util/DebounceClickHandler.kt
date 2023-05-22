/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import javax.inject.Inject

class DebounceClickHandler @Inject constructor() {
    private val debounceMillis = 800L
    var lastClickTime: Long = 0

    fun debounceClick(click: () -> Unit) {
        if (hasTimeFinished()) {
            click()
            lastClickTime = System.currentTimeMillis()
        }
    }

    private fun hasTimeFinished() = System.currentTimeMillis() - lastClickTime > debounceMillis
}
