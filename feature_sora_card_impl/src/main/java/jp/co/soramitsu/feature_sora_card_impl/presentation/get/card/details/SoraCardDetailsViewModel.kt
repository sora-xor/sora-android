package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import javax.inject.Inject

@HiltViewModel
class SoraCardDetailsViewModel @Inject constructor(): BaseViewModel() {

    var soraCardDetailsScreenState: SoraCardDetailsScreenState by mutableStateOf(
        value = SoraCardDetailsScreenState(
            soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
                balance = 3644.50f,
                isCardEnabled = false,
                soraCardMenuActions = SoraCardMenuAction.values().toList()
            ),
            soraCardReferralBannerCardState = SoraCardReferralBannerCardState(),
            soraCardRecentActivitiesCardState = SoraCardRecentActivitiesCardState(
                data = listOf(
                    SoraCardRecentActivity(
                        t = "Something"
                    )
                )
            ),
            soraCardIBANCardState = SoraCardIBANCardState(
                iban = "LT61 3250 0467 7252 5583"
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
                title = R.string.qr_code,
                navIcon = R.drawable.ic_cross,
            )
        )
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

    fun onRecentActivityClick() {
        /* Functionality will be added in further releases */
    }

    fun onShowMoreRecentActivitiesClick() {
        /* Functionality will be added in further releases */
    }

    fun onIbanCardActionClick() {
        /* Functionality will be added in further releases */
    }

    fun onSettingsOptionClick() {
        // TODO add log out functionality
    }

}