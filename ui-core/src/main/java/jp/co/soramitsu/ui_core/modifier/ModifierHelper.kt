package jp.co.soramitsu.ui_core.modifier

import androidx.compose.ui.Modifier

fun Modifier.applyIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier =
    if (condition) then(block(Modifier)) else this
