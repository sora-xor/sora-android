package jp.co.soramitsu.ui_core.component.wrappedtext

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Stable

@Stable
data class WrappedTextState(
    val text: String = "",
    val title: String? = null,
    @DrawableRes val trailingIcon: Int? = null,
    @DrawableRes val leadingIcon: Int? = null,
)
