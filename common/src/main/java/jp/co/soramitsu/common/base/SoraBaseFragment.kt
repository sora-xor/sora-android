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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
import jp.co.soramitsu.common.account.AccountOptionsRouter
import jp.co.soramitsu.common.account.SelectedAccountSigningSource
import jp.co.soramitsu.common.domain.BarsColorHandler
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.components.AlertDialogContent
import jp.co.soramitsu.common.presentation.compose.components.ConfirmDialogContent
import jp.co.soramitsu.common.presentation.compose.components.Toolbar
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SelectedAccountSigningHost
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.AppStoreListing
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

    @Inject
    lateinit var selectedAccountSigningSource: SelectedAccountSigningSource

    @Inject
    lateinit var accountOptionsRouter: AccountOptionsRouter

    /**
     * True for a screen where the person signs with the selected account (send, swap, liquidity,
     * referral, staking, claim). When that account cannot sign on this phone, the screen says so
     * above its form, before anything is filled in, and offers the repair (Account options).
     */
    open val explainsReadOnlyAccount: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(this) {
            onBack()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.safeCast<BarsColorHandler>()?.setColor(backgroundColor())
        // A result the view model held while this screen was not in front is shown now.
        viewModel.onScreenResumed()
    }

    override fun onPause() {
        // From here on the view model opens nothing and closes nothing until the next resume.
        viewModel.onScreenPaused()
        super.onPause()
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
                    val openConfirmDialog = remember { mutableStateOf<ConfirmDialogData?>(null) }

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
                        viewModel.confirmDialogLiveData.observe {
                            openConfirmDialog.value = it
                        }
                        viewModel.openAppStoreLiveData.observe {
                            AppStoreListing.open(requireContext())
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

                    Box(Modifier.fillMaxSize()) {
                        NeonBackground(safe = useSafeAurora)
                        Scaffold(
                            scaffoldState = scaffoldState,
                            backgroundColor = Color.Transparent,
                            topBar = {
                                Toolbar(
                                    toolbarState = viewModel.toolbarState.observeAsState().value,
                                    scrollState = scrollState,
                                    backgroundColor = Color.Transparent,
                                    tintColor = MaterialTheme.customColors.fgPrimary,
                                    onNavClick = { debounceClickHandler.debounceClick(::onNavClicked) },
                                    onActionClick = viewModel::onAction,
                                    onMenuItemClick = viewModel::onMenuItem,
                                    onSearch = viewModel::onToolbarSearch,
                                )
                            }
                        ) { padding ->
                            // Whether the selected account can sign on this phone, for every
                            // screen this fragment hosts: the notice above a signing form, and the
                            // state its action button reads.
                            SelectedAccountSigningHost(
                                source = selectedAccountSigningSource,
                                explainsReadOnlyAccount = explainsReadOnlyAccount,
                                onRepair = accountOptionsRouter::showAccountDetails,
                                modifier = Modifier.padding(padding),
                            ) {
                                NavHost(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    navController = navController,
                                    contentAlignment = Alignment.TopCenter,
                                    startDestination = viewModel.startScreen(),
                                ) {
                                    content(scrollState, navController)
                                }
                            }
                            AlertDialogContent(openAlertDialog)
                            ConfirmDialogContent(openConfirmDialog)
                        }
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

    protected open val useSafeAurora: Boolean = false

    abstract fun NavGraphBuilder.content(scrollState: ScrollState, navController: NavHostController)

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }
}
