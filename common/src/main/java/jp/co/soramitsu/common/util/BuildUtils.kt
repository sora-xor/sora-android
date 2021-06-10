package jp.co.soramitsu.common.util

import jp.co.soramitsu.common.BuildConfig

object BuildUtils {
    fun isFlavors(vararg flavors: Flavor): Boolean =
        BuildConfig.FLAVOR in flavors.map { it.flavorName }
}

enum class Flavor(val flavorName: String) {
    DEVELOP("develop"),
    STAGE("staging"),
    PROD("production"),
    SORALUTION("tst")
}
