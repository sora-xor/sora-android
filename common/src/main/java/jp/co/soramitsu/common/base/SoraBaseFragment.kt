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

package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarResult
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.findNavController
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.BarsColorHandler
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.components.AlertDialogContent
import jp.co.soramitsu.common.presentation.compose.components.Toolbar
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.ui_core.theme.customColors
import kotlinx.coroutines.launch

const val theOnlyRoute = "theOnlyRoute"

abstract class SoraBaseFragment<T : BaseViewModel> : Fragment() {

    abstract val viewModel: T

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(this) {
            onBack()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.safeCast<BarsColorHandler>()?.setColor(backgroundColor())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (BuildUtils.sdkAtLeast(Build.VERSION_CODES.TIRAMISU).not()) {
            viewModel.copiedToast.observe {
                Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                val isDarkModeOn: State<Boolean> =
                    darkThemeManager.darkModeStatusFlow.collectAsState()

                SoraAppTheme(
                    darkTheme = isDarkModeOn.value
                ) {
                    val scaffoldState = rememberScaffoldState()
                    val scrollState = rememberScrollState()
                    val coroutineScope = rememberCoroutineScope()
                    val openAlertDialog = remember { mutableStateOf(AlertDialogData()) }

                    val navController = rememberNavController()
                    LaunchedEffect(Unit) {
                        navController.addOnDestinationChangedListener { _, destination, _ ->
                            destination.route?.let {
                                viewModel.setCurDestination(it)
                                onDestinationChanged(it)
                            }
                        }
                        viewModel.navigationPop.observe {
                            var popResult = navController.popBackStack()
                            if (!popResult) {
                                popResult = findNavController().popBackStack()
                                if (!popResult) {
                                    activity?.finish()
                                }
                            }
                        }
                        viewModel.navEvent.observe {
                            navController.navigate(route = it.first) {
                                it.second.invoke(this)
                            }
                        }
                        viewModel.navToStart.observe {
                            with(navController) {
                                popBackStack(graph.startDestinationId, true)
                                graph.setStartDestination(it)
                                navigate(it)
                            }
//                            with(navController) {
//                                navigate(it) {
//                                    popUpTo(graph.startDestinationId) {inclusive = true}
//                                }
//                                graph.setStartDestination(it)
//                            }
                        }
                        viewModel.errorLiveData.observe {
                            openAlertDialog.value = AlertDialogData(
                                title = R.string.common_error_general_title,
                                message = it,
                            )
                        }
                        viewModel.alertDialogLiveData.observe {
                            it.let { event ->
                                openAlertDialog.value = AlertDialogData(
                                    title = event.first,
                                    message = event.second,
                                )
                            }
                        }
                        viewModel.errorFromResourceLiveData.observe {
                            val (title, message) = it

                            openAlertDialog.value = AlertDialogData(
                                title = title,
                                message = message,
                            )
                        }

                        viewModel.snackBarLiveData.observe {
                            coroutineScope.launch {
                                when (
                                    scaffoldState.snackbarHostState.showSnackbar(
                                        it.title,
                                        it.actionText
                                    )
                                ) {
                                    SnackbarResult.Dismissed -> {
                                        it.onCancelHandler()
                                    }

                                    SnackbarResult.ActionPerformed -> {
                                        it.onActionHandler()
                                    }
                                }
                            }
                        }
                    }

                    Scaffold(
                        scaffoldState = scaffoldState,
                        backgroundColor = backgroundColorComposable(),
                        topBar = {
                            Toolbar(
                                toolbarState = viewModel.toolbarState.observeAsState().value,
                                scrollState = scrollState,
                                backgroundColor = backgroundColorComposable(),
                                tintColor = MaterialTheme.customColors.fgPrimary,
                                onNavClick = { debounceClickHandler.debounceClick(::onNavClicked) },
                                onActionClick = viewModel::onAction,
                                onMenuItemClick = viewModel::onMenuItem,
                                onSearch = viewModel::onToolbarSearch,
                            )
                        }
                    ) { padding ->
                        NavHost(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize(),
                            navController = navController,
                            contentAlignment = Alignment.TopCenter,
                            startDestination = viewModel.startScreen(),
                        ) {
                            content(scrollState, navController)
                        }
                        AlertDialogContent(openAlertDialog)
                    }
                }
            }
        }
    }

    @Composable
    open fun backgroundColorComposable() = MaterialTheme.customColors.bgPage

    open fun backgroundColor() = R.attr.baseBackground

    open fun onDestinationChanged(destination: String) {}

    open fun onBack() {
        viewModel.onBackPressed()
    }

    open fun onNavClicked() {
        viewModel.onNavIcon()
    }

    abstract fun NavGraphBuilder.content(scrollState: ScrollState, navController: NavHostController)

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }
}
