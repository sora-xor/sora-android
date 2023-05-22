/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
