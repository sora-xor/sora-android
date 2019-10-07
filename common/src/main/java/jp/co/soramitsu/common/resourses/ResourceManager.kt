/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import androidx.annotation.StringRes

interface ResourceManager {

    fun getString(@StringRes resource: Int): String
}