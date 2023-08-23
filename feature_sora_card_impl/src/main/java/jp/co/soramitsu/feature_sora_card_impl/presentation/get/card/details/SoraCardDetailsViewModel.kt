package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.launch

@HiltViewModel
class SoraCardDetailsViewModel @Inject constructor(
    private val soraCardInteractor: SoraCardInteractor,
    private val clipboardManager: ClipboardManager,
) : BaseViewModel() {

    private val _soraCardLogOutDialogState = MutableLiveData<Unit>()
    val soraCardLogOutDialogState: LiveData<Unit> = _soraCardLogOutDialogState

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val _shareLinkEvent = SingleLiveEvent<String>()
    val shareLinkEvent: LiveData<String> = _shareLinkEvent

    private var ibanCache: String? = null

    var soraCardDetailsScreenState: SoraCardDetailsScreenState by mutableStateOf(
        value = SoraCardDetailsScreenState(
            soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
                balance = 0f,
                isCardEnabled = false,
                soraCardMenuActions = SoraCardMenuAction.values().toList()
            ),
            soraCardSettingsCard = SoraCardSettingsCardState(
                soraCardSettingsOptions = SoraCardSettingsOption.values().toList()
            )
        )
    )
        private set

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.sora_card_details_title,
                navIcon = R.drawable.ic_cross,
            )
        )

        viewModelScope.launch {
            tryCatch {
                soraCardInteractor.fetchUserIbanAccount()
                    .onSuccess { iban ->
                        ibanCache = iban
                        soraCardDetailsScreenState = soraCardDetailsScreenState.copy(
                            soraCardIBANCardState = SoraCardIBANCardState(iban)
                        )
                    }
                    .onFailure {
                        onError(it)
                    }
            }
        }
    }

    fun onShowSoraCardDetailsClick() {
        /* Functionality will be added in further releases */
    }

    fun onSoraCardMenuActionClick(position: Int) {
        /* Functionality will be added in further releases */
    }

    fun onReferralBannerClick() {
        /* Functionality will be added in further releases */
    }

    fun onCloseReferralBannerClick() {
        /* Functionality will be added in further releases */
    }

    fun onRecentActivityClick(position: Int) {
        /* Functionality will be added in further releases */
    }

    fun onShowMoreRecentActivitiesClick() {
        /* Functionality will be added in further releases */
    }

    fun onIbanCardShareClick() {
        ibanCache?.let {
            _shareLinkEvent.value = it
        }
    }

    fun onIbanCardClick() {
        ibanCache?.let {
            clipboardManager.addToClipboard("iban", it)
            _copiedAddressEvent.trigger()
        }
    }

    fun onSettingsOptionClick(position: Int) {
        val settings = soraCardDetailsScreenState.soraCardSettingsCard
            ?.soraCardSettingsOptions ?: return

        when (settings[position]) {
            SoraCardSettingsOption.LOG_OUT -> _soraCardLogOutDialogState.value = Unit
        }
    }

    fun onSoraCardLogOutClick() {
        viewModelScope.launch {
            tryCatch {
                soraCardInteractor.logOutFromSoraCard()
            }
        }.invokeOnCompletion {
            if (it == null)
                onNavIcon()
        }
    }
}
