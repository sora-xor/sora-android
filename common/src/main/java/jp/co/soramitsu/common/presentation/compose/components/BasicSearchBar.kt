/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.searchbar.SearchBar
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun BasicSearchBar(
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    placeholder: String,
    action: String,
    onAction: () -> Unit,
    onClear: () -> Unit,
    onSearch: (String) -> Unit,
    onNavigate: () -> Unit
) {
    val searchValue = remember { mutableStateOf(TextFieldValue("")) }
    SearchBar(
        backgroundColor = backgroundColor,
        elevation = 0.dp,
        navIcon = painterResource(id = R.drawable.ic_cross),
        onNavigate = onNavigate,
        searchValue = searchValue.value,
        searchPlaceholder = placeholder,
        actionLabel = action,
        onSearch = {
            searchValue.value = it
            onSearch.invoke(it.text)
        },
        onClear = {
            searchValue.value = TextFieldValue("")
            onClear.invoke()
        },
        onAction = onAction,
    )
}

@Preview
@Composable
private fun Preview() {
    BasicSearchBar(
        placeholder = "Placeholder",
        action = "Action",
        onAction = { /*TODO*/ },
        onClear = { /*TODO*/ },
        onSearch = { /*TODO*/ }
    ) {
        /*TODO*/
    }
}
