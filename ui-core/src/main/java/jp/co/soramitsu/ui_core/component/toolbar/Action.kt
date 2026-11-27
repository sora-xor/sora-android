package jp.co.soramitsu.ui_core.component.toolbar

import jp.co.soramitsu.ui_core.R

sealed interface Action {

    val icon: Int

    class Info(override val icon: Int = R.drawable.ic_info): Action
    class Scan(override val icon: Int = R.drawable.ic_scan): Action
    class Share(override val icon: Int = R.drawable.ic_share): Action
    class Close(override val icon: Int = R.drawable.ic_cross_24) : Action
    class Plus(override val icon: Int = R.drawable.ic_plus) : Action
}
