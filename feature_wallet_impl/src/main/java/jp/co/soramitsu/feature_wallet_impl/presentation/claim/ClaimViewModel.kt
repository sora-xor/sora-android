package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClaimViewModel(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _buttonPendingStatusLiveData = MutableLiveData<Boolean>()
    val buttonPendingStatusLiveData: LiveData<Boolean> = _buttonPendingStatusLiveData

    private val _openSendEmailEvent = SingleLiveEvent<String>()
    val openSendEmailEvent: LiveData<String> = _openSendEmailEvent

    init {
        viewModelScope.launch {
            walletInteractor.observeMigrationStatus().collectLatest {
                _buttonPendingStatusLiveData.value = false
                when (it) {
                    MigrationStatus.NOT_INITIATED -> {
                    }
                    MigrationStatus.FAILED -> onError(R.string.common_error_general_message)
                    MigrationStatus.SUCCESS -> router.popBackStackFragment()
                }
            }
        }
    }

    fun checkMigrationIsAlreadyFinished() {
        viewModelScope.launch {
            if (!walletInteractor.needsMigration()) router.popBackStackFragment()
        }
    }

    fun contactsUsClicked() {
        _openSendEmailEvent.postValue(BuildConfig.EMAIL)
    }

    fun nextButtonClicked(context: Context) {
        _buttonPendingStatusLiveData.value = true
        ClaimWorker.start(context)
    }
}
