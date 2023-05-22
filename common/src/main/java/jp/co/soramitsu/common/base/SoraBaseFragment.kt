/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.navigation.findNavController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.BarsColorhandler
import jp.co.soramitsu.common.presentation.compose.components.AlertDialogContent
import jp.co.soramitsu.common.presentation.compose.components.Toolbar
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.ui_core.theme.customColors
import kotlinx.coroutines.launch

const val theOnlyRoute = "theOnlyRoute"

@OptIn(ExperimentalAnimationApi::class)
abstract class SoraBaseFragment<T : BaseViewModel> : Fragment() {

    abstract val viewModel: T

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    override fun onResume() {
        super.onResume()
        activity?.safeCast<BarsColorhandler>()?.setColor(backgroundColor())
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                SoraAppTheme {
                    val scaffoldState = rememberScaffoldState()
                    val scrollState = rememberScrollState()
                    val coroutineScope = rememberCoroutineScope()
                    val openAlertDialog = remember { mutableStateOf(AlertDialogData()) }

                    val navController = rememberAnimatedNavController()
                    LaunchedEffect(Unit) {
                        navController.addOnDestinationChangedListener { _, destination, _ ->
                            destination.route?.let {
                                viewModel.setCurDestination(it)
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
                                when (scaffoldState.snackbarHostState.showSnackbar(it.title, it.actionText)) {
                                    SnackbarResult.Dismissed -> { it.onCancelHandler() }
                                    SnackbarResult.ActionPerformed -> { it.onActionHandler() }
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
                            )
                        }
                    ) { padding ->
                        BackHandler {
                            onBack()
                        }
                        AnimatedNavHost(
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
