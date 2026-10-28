/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.presentation.view

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.AlertDialogData
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.components.AlertDialogContent
import jp.co.soramitsu.common.presentation.compose.components.Toolbar
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.setNavigationBarColorCompat
import jp.co.soramitsu.common.util.ext.setStatusBarColorCompat
import jp.co.soramitsu.ui_core.theme.customColors

abstract class SoraBaseActivity<T : BaseViewModel> : AppCompatActivity() {

    abstract val viewModel: T

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val isDarkModeOn: State<Boolean> =
                darkThemeManager.darkModeStatusFlow.collectAsState()

            SoraAppTheme(
                darkTheme = isDarkModeOn.value
            ) {
                val scaffoldState = rememberScaffoldState()
                val scrollState = rememberScrollState()
                val openAlertDialog = remember { mutableStateOf(AlertDialogData()) }

                LaunchedEffect(Unit) {
                    viewModel.navigationPop.observe(this@SoraBaseActivity) {
                        onToolbarNavigation()
                    }
                    viewModel.errorLiveData.observe(this@SoraBaseActivity) {
                        openAlertDialog.value = AlertDialogData(
                            title = getString(R.string.common_error_general_title),
                            message = it
                        )
                    }
                    viewModel.alertDialogLiveData.observe(this@SoraBaseActivity) {
                        it.let { event ->
                            openAlertDialog.value = AlertDialogData(
                                title = event.first,
                                message = event.second
                            )
                        }
                    }
                    viewModel.errorFromResourceLiveData.observe(this@SoraBaseActivity) {
                        val (title, message) = it
                        openAlertDialog.value = AlertDialogData(
                            title = getString(title),
                            message = getString(message)
                        )
                    }
                }

                BackHandler {
                    viewModel.onBackPressed()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Static SORA Neon aurora in both themes (replaces the full-screen bitmap).
                    NeonBackground(safe = useSafeAurora())
                    Scaffold(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .statusBarsPadding()
                            .imePadding(),
                        scaffoldState = scaffoldState,
                        backgroundColor = MaterialTheme.customColors.bgPage.copy(alpha = 0f),
                        topBar = {
                            Toolbar(
                                toolbarState = viewModel.toolbarState.observeAsState().value,
                                scrollState = scrollState,
                                backgroundColor = MaterialTheme.customColors.bgPage.copy(alpha = 0f),
                                tintColor = MaterialTheme.customColors.fgPrimary,
                                onNavClick = viewModel::onNavIcon,
                                onActionClick = viewModel::onAction
                            )
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Content(padding, scrollState)

                            AlertDialogContent(openAlertDialog)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val background = attrColor(backgroundColor())
        window.setStatusBarColorCompat(background)
        window.setNavigationBarColorCompat(background)
    }

    abstract fun onToolbarNavigation()

    open fun backgroundColor() = R.attr.baseBackground

    @Composable
    protected open fun useSafeAurora(): Boolean = false

    @Composable
    abstract fun Content(padding: PaddingValues, scrollState: ScrollState)
}
