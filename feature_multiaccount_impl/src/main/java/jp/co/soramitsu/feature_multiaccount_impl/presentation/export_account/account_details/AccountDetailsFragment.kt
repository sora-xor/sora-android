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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.args.address
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password.BackupPasswordScreen
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class AccountDetailsFragment : SoraBaseFragment<AccountDetailsViewModel>() {

    override val useSafeAurora = true

    @Inject
    lateinit var vmf: AccountDetailsViewModel.AccountDetailsViewModelFactory

    private var launcher: ActivityResultLauncher<Intent>? = null
    private var consentHandlerLauncher: ActivityResultLauncher<Intent>? = null

    override val viewModel: AccountDetailsViewModel by viewModels {
        CustomViewModelFactory { vmf.create(requireArguments().address) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onSuccessfulGoogleSignin()
            }
        }

        consentHandlerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onSuccessfulConsent()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.consentExceptionHandler.observe {
            consentHandlerLauncher?.launch(it)
        }
        viewModel.restoreKeysEvent.observe { how ->
            // Import account opens at the chosen step. Entering this account's recovery phrase
            // (or restoring its Google backup) there repairs its keys in place.
            OnboardingActivity.start(
                context = requireContext(),
                isClearTask = false,
                entry = when (how) {
                    AccountDetailsViewModel.RestoreKeys.WITH_PHRASE ->
                        OnboardingActivity.Entry.IMPORT_RECOVERY_PHRASE
                    AccountDetailsViewModel.RestoreKeys.CHOOSE_SOURCE ->
                        OnboardingActivity.Entry.IMPORT_CHOOSE_SOURCE
                    AccountDetailsViewModel.RestoreKeys.FROM_GOOGLE ->
                        OnboardingActivity.Entry.IMPORT_FROM_GOOGLE
                },
            )
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        animatedComposable(
            route = AccountDetailsRoutes.ACCOUNT_DETAILS,
        ) {
            viewModel.deleteDialogState.observeAsState().value?.let {
                if (it) {
                    AlertDialog(
                        backgroundColor = MaterialTheme.customColors.bgPage,
                        title = {
                            Text(
                                text = stringResource(id = R.string.delete_backup_alert_title),
                                color = MaterialTheme.customColors.fgPrimary,
                                style = MaterialTheme.customTypography.textSBold
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(id = R.string.delete_backup_alert_description),
                                color = MaterialTheme.customColors.fgPrimary,
                                style = MaterialTheme.customTypography.paragraphSBold
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = viewModel::deleteGoogleBackup
                            ) {
                                Text(
                                    text = stringResource(id = R.string.common_delete),
                                    color = MaterialTheme.customColors.statusError,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = viewModel::deleteDialogDismiss
                            ) {
                                Text(
                                    text = stringResource(id = R.string.common_cancel),
                                )
                            }
                        },
                        onDismissRequest = viewModel::deleteDialogDismiss
                    )
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.x2)
            ) {
                viewModel.accountDetailsScreenState.observeAsState().value?.let {
                    AccountDetailsScreenBasic(
                        it,
                        viewModel::onNameChange,
                        viewModel::onShowPassphrase,
                        viewModel::onShowRawSeed,
                        viewModel::onExportJson,
                        {
                            debounceClickHandler.debounceClick {
                                viewModel.onBackupClicked(
                                    launcher
                                )
                            }
                        },
                        viewModel::onLogout,
                        viewModel::onAddressCopy,
                        onRestoreKeysWithPhrase = viewModel::onRestoreKeysWithPhrase,
                        onRestoreKeysFromGoogle = viewModel::onRestoreKeysFromGoogle,
                        onShowSavedBackup = viewModel::onShowSavedBackup,
                        onRestoreKeysAnotherWay = viewModel::onRestoreKeysAnotherWay,
                    )
                }
            }
        }

        animatedComposable(
            route = AccountDetailsRoutes.BACKUP_ACCOUNT,
        ) {
            Column(
                modifier = Modifier.verticalScroll(scrollState)
            ) {
                viewModel.createBackupPasswordState.observeAsState().value?.let {
                    Box {
                        BackupPasswordScreen(
                            it,
                            viewModel::onBackupPasswordChanged,
                            viewModel::onBackupPasswordConfirmationChanged,
                            viewModel::onWarningToggle
                        ) {
                            debounceClickHandler.debounceClick {
                                viewModel.onBackupPasswordClicked()
                            }
                        }
                    }
                }
            }
        }
    }
}
