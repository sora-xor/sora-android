package jp.co.soramitsu.common.presentation.view.chooserbottomsheet

data class ChooserItem(
    val title: Int,
    val icon: Int = 0,
    val selected: Boolean = false,
    val clickHandler: () -> Unit
)
