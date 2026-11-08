package jp.co.soramitsu.ui_core.component.searchview

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue

@Stable
data class SearchViewState(
    val value: TextFieldValue = TextFieldValue(""),
    val placeholder: String? = null,
    val enabled: Boolean = true,
    val focused: Boolean = false,
)
