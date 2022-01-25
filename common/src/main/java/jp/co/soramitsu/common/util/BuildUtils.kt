/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.common.BuildConfig

object BuildUtils {
    fun isFlavors(vararg flavors: Flavor): Boolean =
        BuildConfig.FLAVOR in flavors.map { it.flavorName }
}

enum class Flavor(val flavorName: String) {
    DEVELOP("develop"),
    TESTING("testing"),
    PROD("production"),
    SORALUTION("soralution")
}
