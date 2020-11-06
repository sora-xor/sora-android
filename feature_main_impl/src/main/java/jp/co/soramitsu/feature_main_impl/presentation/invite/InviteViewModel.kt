package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor

class InviteViewModel(
    private val interactor: InvitationInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
    private val deviceParamsProvider: DeviceParamsProvider,
    invitationHandler: InvitationHandler,
    private val timer: TimerWrapper,
    private val resourceManager: ResourceManager
) : BaseViewModel(), WithProgress by progress {

    private val _hideSwipeRefreshEventLiveData = MutableLiveData<Event<Unit>>()
    val hideSwipeRefreshEventLiveData: LiveData<Event<Unit>> = _hideSwipeRefreshEventLiveData

    private val _enterInviteCodeButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val enterInviteCodeButtonVisibilityLiveData: LiveData<Boolean> = _enterInviteCodeButtonVisibilityLiveData

    private val _parentUserLiveData = MutableLiveData<InvitedUser>()
    val parentUserLiveData: LiveData<InvitedUser> = _parentUserLiveData

    private val _invitedUsersLiveData = MutableLiveData<List<InvitedUser>>()
    val invitedUsersLiveData: LiveData<List<InvitedUser>> = _invitedUsersLiveData

    private val _enteredCodeAppliedLiveData = MutableLiveData<Event<Unit>>()
    val enteredCodeAppliedLiveData: LiveData<Event<Unit>> = _enteredCodeAppliedLiveData

    private val _shareCodeLiveData = MutableLiveData<String>()
    val shareCodeLiveData: LiveData<String> = _shareCodeLiveData

    private val _showInvitationDialogLiveData = MutableLiveData<Event<Unit>>()
    val showInvitationDialogLiveData: LiveData<Event<Unit>> = _showInvitationDialogLiveData

    private val _timerFormattedLiveData = MutableLiveData<Pair<String, Int>>()
    val timerFormattedLiveData: LiveData<Pair<String, Int>> = _timerFormattedLiveData

    private var timerDisposable: Disposable? = null

    init {
        disposables.add(
            invitationHandler.observeInvitationApplies()
                .flatMapSingle { interactor.updateInvitationInfo() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ invitations ->
                    invitations.parentInvitations?.let { _parentUserLiveData.value = it }
                    _enterInviteCodeButtonVisibilityLiveData.value = false
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun loadUserInviteInfo(updateCached: Boolean) {
        disposables.add(
            interactor.getUserInviteInfo(updateCached)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { if (!updateCached) loadUserInviteInfo(true) }
                .subscribe({
                    _hideSwipeRefreshEventLiveData.value = Event(Unit)

                    val user = it.first
                    val currentTimeIsLessThanInviteExpiration = user.inviteAcceptExpirationMomentMillis > deviceParamsProvider.getCurrentTimeMillis()
                    val enterInviteAvailable = user.parentId.isEmpty() && currentTimeIsLessThanInviteExpiration
                    _enterInviteCodeButtonVisibilityLiveData.value = enterInviteAvailable

                    if (enterInviteAvailable) {
                        val addInvitationTimeLeftMillis = user.inviteAcceptExpirationMomentMillis - deviceParamsProvider.getCurrentTimeMillis()
                        startTimer(addInvitationTimeLeftMillis)
                    }

                    val invitations = it.second
                    invitations.parentInvitations?.let { _parentUserLiveData.value = it }
                    _invitedUsersLiveData.value = invitations.acceptedInviteVms
                }, {
                    logException(it)
                })
        )
    }

    private fun startTimer(addInvitationTimeLeftMillis: Long) {
        if (timerDisposable != null && !timerDisposable!!.isDisposed) {
            timerDisposable!!.dispose()
        }
        timerDisposable = timer.start(addInvitationTimeLeftMillis)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                val timeColor = if (it < 60 * 60 * 1000) {
                    resourceManager.getColor(R.color.uikit_lightRed)
                } else {
                    resourceManager.getColor(R.color.grey)
                }
                val timeLeft = timer.formatTime(it)
                _timerFormattedLiveData.value = Pair(timeLeft, timeColor)
            }, {
                it.printStackTrace()
            }, {
                _enterInviteCodeButtonVisibilityLiveData.value = false
            })
    }

    fun sendInviteClick() {
        disposables.add(
            interactor.getInviteLink()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompose())
                .subscribe({
                    _shareCodeLiveData.value = it
                }, {
                    onError(it)
                })
        )
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun addInvitationClicked() {
        _showInvitationDialogLiveData.value = Event(Unit)
    }

    fun invitationCodeEntered(inviteCode: String) {
        if (inviteCode.trim().isEmpty()) {
            return
        }
        disposables.add(
            interactor.enterInviteCode(inviteCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompose())
                .subscribe({ invitations ->
                    invitations.parentInvitations?.let { _parentUserLiveData.value = it }
                    _enterInviteCodeButtonVisibilityLiveData.value = false
                    _enteredCodeAppliedLiveData.value = Event(Unit)
                }, {
                    onError(it)
                })
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (timerDisposable != null && !timerDisposable!!.isDisposed) timerDisposable!!.dispose()
    }
}