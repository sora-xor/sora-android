/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class InviteViewModel(
    private val interactor: InvitationInteractor,
    private val router: MainRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    val parentInvitationLiveData = MutableLiveData<InvitedUser>()
    val invitedUsersLiveData = MutableLiveData<List<InvitedUser>>()
    val invitationsLeftLiveData = MutableLiveData<Int>()
    val shareCodeLiveData = MutableLiveData<String>()
    val hideSwipeProgressLiveData = MutableLiveData<Event<Unit>>()

    fun refreshData(isSwiped: Boolean) {
        disposables.add(
            Completable.mergeArray(loadInvitedUsers(isSwiped), loadInvitationsLeft(isSwiped))
                .doFinally { if (!isSwiped) refreshData(true) }
                .subscribe({
                    hideSwipeProgressLiveData.value = Event(Unit)
                }, {
                    logException(it)
                })
        )
    }

    private fun loadInvitedUsers(updateCached: Boolean): Completable {
        return interactor.getInvitedUsers(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                it.parentInvitations?.let { parentInvitationLiveData.value = it }
                invitedUsersLiveData.value = it.acceptedInviteVms
            }
            .ignoreElement()
    }

    private fun loadInvitationsLeft(updateCached: Boolean): Completable {
        return interactor.getInvitationsLeft(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { invitationsLeftLiveData.value = it }
            .ignoreElement()
    }

    fun sendInviteClick() {
        invitationsLeftLiveData.value?.let {
            if (it > 0) {
                disposables.add(
                    interactor.sendInviteCode()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(progressCompose())
                        .subscribe({
                            shareCodeLiveData.value = it.first
                            invitationsLeftLiveData.value = it.second
                        }, {
                            onError(it)
                        })
                )
            } else {
                onError(R.string.not_enough_invitations)
            }
        }
    }

    fun btnHelpClicked() {
        router.showFaq()
    }
}