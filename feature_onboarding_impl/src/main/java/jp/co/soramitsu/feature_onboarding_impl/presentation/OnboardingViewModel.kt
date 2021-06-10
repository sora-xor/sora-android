/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel

class OnboardingViewModel(
    private val invitationHandler: InvitationHandler,
    private val runtimeManager: RuntimeManager,
) : BaseViewModel() {

    init {
        disposables.add(
            runtimeManager.start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {},
                    {
                        logException(it)
                    }
                )
        )
    }

    fun startedWithInviteAction() {
        invitationHandler.invitationApplied()
    }
}
