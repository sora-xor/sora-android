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

package jp.co.soramitsu.feature_main_impl.presentation.language

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.item.SelectableItem
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class SelectLanguageFragment :
    SoraBaseFragment<SelectLanguageViewModel>() {

    override val viewModel: SelectLanguageViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        viewModel.languageChangedLiveData.observe {
            (activity as MainActivity).restartAfterLanguageChange()
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ContentCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = Dimens.x2),
                    innerPadding = PaddingValues(horizontal = Dimens.x3, vertical = Dimens.x1),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .verticalScroll(scrollState)
                    ) {
                        LanguagesList(
                            list = viewModel.state.items,
                            onClick = viewModel::languageSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LanguagesList(
    list: List<LanguageItem>,
    onClick: (LanguageItem) -> Unit,
) {
    list.forEach {
        SelectableItem(
            modifier = Modifier.padding(vertical = Dimens.x2),
            title = it.displayName,
            isSelected = it.isSelected,
            subtitle = it.nativeDisplayName,
            isMenuIconVisible = false,
            onClick = { onClick.invoke(it) },
            menuItems = emptyList(),
            onMenuItemClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLanguagesList() {
    Column {
        LanguagesList(
            list = listOf(
                LanguageItem(
                    iso = "qwe",
                    displayName = "lang 1",
                    nativeDisplayName = "yazyk 1",
                    isSelected = false,
                ),
                LanguageItem(
                    iso = "asd",
                    displayName = "lang 2",
                    nativeDisplayName = "yazyk 2",
                    isSelected = false,
                ),
                LanguageItem(
                    iso = "zxc",
                    displayName = "lang 3",
                    nativeDisplayName = "yazyk 3",
                    isSelected = true,
                ),
            ),
            onClick = {},
        )
    }
}
