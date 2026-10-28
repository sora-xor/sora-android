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

package jp.co.soramitsu.common.util

import android.os.Build
import jp.co.soramitsu.common.BuildConfig

object BuildUtils {
    fun isFlavors(vararg flavors: Flavor): Boolean =
        BuildConfig.FLAVOR in flavors.map { it.flavorName }
    fun isBuildType(vararg types: BuildType): Boolean =
        BuildConfig.BUILD_TYPE in types.map { it.type }

    @androidx.annotation.ChecksSdkIntAtLeast(parameter = 0)
    fun sdkAtLeast(v: Int): Boolean = Build.VERSION.SDK_INT >= v

    fun isPlayMarket(): Boolean = isBuildType(BuildType.RELEASE) && isFlavors(Flavor.PROD, Flavor.SORALUTION)
    fun isProdPlayMarket(): Boolean = isBuildType(BuildType.RELEASE) && isFlavors(Flavor.PROD)
}

enum class BuildType(val type: String) {
    RELEASE("release"),
    DEBUG("debug"),
    FIREBASE("firebasedebug")
}

enum class Flavor(val flavorName: String) {
    DEVELOP("develop"),
    PROD("production"),
    SORALUTION("soralution")
}
