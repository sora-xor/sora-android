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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import android.app.Activity
import android.os.Bundle
import android.view.View
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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.args.address
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password.BackupPasswordScreen
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class AccountDetailsFragment : SoraBaseFragment<AccountDetailsViewModel>() {

    @Inject
    lateinit var vmf: AccountDetailsViewModel.AccountDetailsViewModelFactory

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onSuccessfulGoogleSignin()
            }
        }

    private val consentHandlerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onSuccessfulConsent()
            }
        }

    override val viewModel: AccountDetailsViewModel by viewModels {
        CustomViewModelFactory { vmf.create(requireArguments().address) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.consentExceptionHandler.observe {
            consentHandlerLauncher.launch(it)
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
