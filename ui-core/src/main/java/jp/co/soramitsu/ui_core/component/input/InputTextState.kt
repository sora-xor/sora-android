package jp.co.soramitsu.ui_core.component.input

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue

@Stable
data class InputTextState(
    val value: TextFieldValue = TextFieldValue(""),
    val label: Any? = null,
    val placeholder: String? = null,
    val descriptionText: Any? = null,
    @DrawableRes val trailingIcon: Int? = null,
    @DrawableRes val leadingIcon: Int? = null,
    val enabled: Boolean = true,
    val error: Boolean = false,
    val success: Boolean = false,
)
