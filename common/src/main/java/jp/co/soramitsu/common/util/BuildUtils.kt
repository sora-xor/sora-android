/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.os.Build
import jp.co.soramitsu.common.BuildConfig

object BuildUtils {
    fun isFlavors(vararg flavors: Flavor): Boolean =
        BuildConfig.FLAVOR in flavors.map { it.flavorName }
    fun isBuildType(vararg types: BuildType): Boolean =
        BuildConfig.BUILD_TYPE in types.map { it.type }

    fun sdkAtLeast(v: Int): Boolean = Build.VERSION.SDK_INT >= v

    fun isPlayMarket(): Boolean = isBuildType(BuildType.RELEASE) && isFlavors(Flavor.PROD, Flavor.SORALUTION)
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
