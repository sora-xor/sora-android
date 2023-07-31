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

package jp.co.soramitsu.common.presentation.view

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.AlertDialogData
import jp.co.soramitsu.common.presentation.compose.components.AlertDialogContent
import jp.co.soramitsu.common.presentation.compose.components.Toolbar
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.ui_core.theme.customColors

abstract class SoraBaseActivity<T : BaseViewModel> : AppCompatActivity() {

    abstract val viewModel: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoraAppTheme {
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
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        painter = painterResource(id = R.drawable.bg_image),
                        contentDescription = ""
                    )
                    Scaffold(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .statusBarsPadding()
                            .imePadding(),
                        scaffoldState = scaffoldState,
                        backgroundColor = MaterialTheme.customColors.fgPrimary.copy(alpha = 0f),
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

    abstract fun onToolbarNavigation()

    @Composable
    abstract fun Content(padding: PaddingValues, scrollState: ScrollState)
}
