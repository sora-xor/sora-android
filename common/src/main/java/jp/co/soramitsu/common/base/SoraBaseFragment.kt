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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.AlertDialog
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.ui_core.component.toolbar.ToolbarCenterAligned
import jp.co.soramitsu.ui_core.component.toolbar.ToolbarLarge
import jp.co.soramitsu.ui_core.component.toolbar.ToolbarMedium
import jp.co.soramitsu.ui_core.component.toolbar.ToolbarSmall
import jp.co.soramitsu.ui_core.theme.customColors

abstract class SoraBaseFragment<T : BaseViewModel> : Fragment() {

    abstract val viewModel: T

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
                    val openAlertDialog = remember { mutableStateOf(AlertDialogData()) }

                    Scaffold(
                        scaffoldState = scaffoldState,
                        topBar = { Toolbar(scrollState) }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.customColors.bgPage)
                                .fillMaxSize()
                        ) {
                            Content(padding, scrollState)

                            AlertDialogContent(openAlertDialog)
                            viewModel.errorLiveData.observeAsState().value?.let {
                                openAlertDialog.value = AlertDialogData(
                                    title = stringResource(id = R.string.common_error_general_title),
                                    message = it.peekContent()
                                )
                            }
                            viewModel.alertDialogLiveData.observeAsState().value?.let {
                                it.getContentIfNotHandled()?.let { event ->
                                    openAlertDialog.value = AlertDialogData(
                                        title = event.first,
                                        message = event.second
                                    )
                                }
                            }
                            viewModel.errorFromResourceLiveData.observeAsState().value?.let {
                                val (title, message) = it.peekContent()

                                openAlertDialog.value = AlertDialogData(
                                    title = stringResource(id = title),
                                    message = stringResource(id = message)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AlertDialogContent(openAlertDialog: MutableState<AlertDialogData>) {
        if (openAlertDialog.value.title.isNotEmpty()) {
            AlertDialog(
                title = { Text(text = openAlertDialog.value.title) },
                text = { Text(text = openAlertDialog.value.message) },
                onDismissRequest = {
                    openAlertDialog.value = AlertDialogData()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openAlertDialog.value = AlertDialogData()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.common_ok))
                    }
                }
            )
        }
    }

    @Composable
    abstract fun Content(padding: PaddingValues, scrollState: ScrollState)

    @Composable
    private fun Toolbar(scrollState: ScrollState?) {
        val elevation = remember(scrollState) {
            derivedStateOf {
                if (scrollState == null || scrollState.value == 0) {
                    0.dp
                } else {
                    AppBarDefaults.TopAppBarElevation
                }
            }
        }

        viewModel.toolbarState.observeAsState().value?.let { state ->
            val navigationHandler = ::onToolbarNavigation
            when (state.type) {
                ToolbarType.CENTER_ALIGNED -> {
                    ToolbarCenterAligned(
                        title = state.title,
                        elevation = elevation.value,
                        navIcon = painterResource(state.navIcon),
                        onNavigate = navigationHandler,
                        menu = state.menuActions,
                        onMenuItemClicked = viewModel::onToolbarMenuItemSelected
                    )
                }

                ToolbarType.SMALL -> {
                    ToolbarSmall(
                        title = state.title,
                        elevation = elevation.value,
                        navIcon = painterResource(state.navIcon),
                        onNavigate = navigationHandler,
                        actionLabel = state.action,
                        onAction = viewModel::onToolbarAction,
                        menu = state.menuActions,
                        onMenuItemClicked = viewModel::onToolbarMenuItemSelected
                    )
                }

                ToolbarType.MEDIUM -> {
                    ToolbarMedium(
                        title = state.title,
                        elevation = elevation.value,
                        navIcon = painterResource(state.navIcon),
                        onNavigate = navigationHandler,
                        actionLabel = state.action,
                        onAction = viewModel::onToolbarAction,
                        menu = state.menuActions,
                        onMenuItemClicked = viewModel::onToolbarMenuItemSelected
                    )
                }

                ToolbarType.LARGE -> {
                    ToolbarLarge(
                        title = state.title,
                        elevation = elevation.value,
                        navIcon = painterResource(state.navIcon),
                        onNavigate = navigationHandler,
                        actionLabel = state.action,
                        onAction = viewModel::onToolbarAction,
                        menu = state.menuActions,
                        onMenuItemClicked = viewModel::onToolbarMenuItemSelected
                    )
                }
            }
        }
    }

    open fun onToolbarNavigation() {
        findNavController().popBackStack()
    }

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }
}
