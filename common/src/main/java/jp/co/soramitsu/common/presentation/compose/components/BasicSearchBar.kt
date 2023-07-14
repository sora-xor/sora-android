/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    action: String?,
    onAction: (() -> Unit)?,
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
