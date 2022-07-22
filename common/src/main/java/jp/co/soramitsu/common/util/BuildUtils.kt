/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.common.BuildConfig

object BuildUtils {
    fun isFlavors(vararg flavors: Flavor): Boolean =
        BuildConfig.FLAVOR in flavors.map { it.flavorName }
    fun isBuildType(vararg types: BuildType): Boolean =
        BuildConfig.BUILD_TYPE in types.map { it.type }
}

enum class BuildType(val type: String) {
    RELEASE("release"),
    DEBUG("debug"),
    FIREBASE("firebasedebug")
}

enum class Flavor(val flavorName: String) {
    DEVELOP("develop"),
    TESTING("tsting"),
    PROD("production"),
    SORALUTION("soralution")
}
