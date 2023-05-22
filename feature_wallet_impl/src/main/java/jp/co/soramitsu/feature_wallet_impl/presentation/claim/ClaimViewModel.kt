/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.Manifest
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class ClaimViewModel @Inject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
) : BaseViewModel() {

    private val _openSendEmailEvent = SingleLiveEvent<String>()
    val openSendEmailEvent: LiveData<String> = _openSendEmailEvent

    private val _claimScreenState = SingleLiveEvent<ClaimState>()
    val claimScreenState: LiveData<ClaimState> = _claimScreenState

    init {
        viewModelScope.launch {
            walletInteractor.observeMigrationStatus().collectLatest {
                when (it) {
                    MigrationStatus.NOT_INITIATED -> {
                    }
                    MigrationStatus.FAILED -> onError(R.string.claim_error_title_v1)
                    MigrationStatus.SUCCESS -> router.popBackStackFragment()
                }
            }
        }

        _claimScreenState.value = ClaimState(false)
    }

    fun checkMigrationIsAlreadyFinished() {
        viewModelScope.launch {
            if (!walletInteractor.needsMigration()) router.popBackStackFragment()
        }
    }

    fun contactsUsClicked() {
        _openSendEmailEvent.postValue(OptionsProvider.email)
    }

    fun nextButtonClicked(fragment: Fragment) {
        _claimScreenState.value?.let {
            _claimScreenState.value = it.copy(loading = true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            com.github.florent37.runtimepermission.RuntimePermission
                .askPermission(fragment, Manifest.permission.POST_NOTIFICATIONS)
                .onAccepted {
                    ClaimWorker.start(fragment.requireContext())
                }
                .ask()
        } else {
            ClaimWorker.start(fragment.requireContext())
        }
    }
}
