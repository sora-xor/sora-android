/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.createSoraCardContract
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.greaterThan
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.oauth.base.sdk.SoraCardInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class GetSoraCardViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val walletInteractor: WalletInteractor,
    private val blockExplorerManager: BlockExplorerManager,
    private val walletRouter: WalletRouter,
    private val mainRouter: MainRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val resourceManager: ResourceManager,
    private val formatter: NumbersFormatter,
    connectionManager: ConnectionManager,
) : BaseViewModel() {

    private companion object {
        val KYC_REAL_REQUIRED_BALANCE: BigDecimal = BigDecimal.valueOf(95)
        val KYC_REQUIRED_BALANCE_WITH_BACKLASH: BigDecimal = Big100
    }

    private val _launchSoraCardRegistration = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardRegistration: LiveData<SoraCardContractData> = _launchSoraCardRegistration

    private val _launchSoraCardSignIn = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardSignIn: LiveData<SoraCardContractData> = _launchSoraCardSignIn

    private val xorEuro = MutableStateFlow<Double?>(null)

    var state = mutableStateOf(GetSoraCardState())
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.get_sora_card_title,
        )

        onEuroIndicatorClick()
        subscribeXorBalance()
        subscribeSoraCardInfo()

        connectionManager.connectionState
            .catch { onError(it) }
            .onEach {
                state.value = state.value.copy(connection = it)
            }
            .launchIn(viewModelScope)
    }

    private fun subscribeXorBalance() {
        assetsInteractor
            .subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
            .combine(xorEuro) { a, b -> a to b }
            .catch {
                onError(it)
            }
            .distinctUntilChanged()
            .onEach { assetWithEuro ->
                val asset = assetWithEuro.first
                val soraCoin = assetWithEuro.second
                if (soraCoin == null) {
                    state.value = state.value.copy(
                        xorBalance = asset.balance.transferable,
                        enoughXor = false,
                        xorRatioAvailable = false,
                    )
                } else {
                    val xorRequiredBalanceWithBacklash =
                        KYC_REQUIRED_BALANCE_WITH_BACKLASH.divideBy(BigDecimal.valueOf(soraCoin))
                    val xorRealRequiredBalance =
                        KYC_REAL_REQUIRED_BALANCE.divideBy(BigDecimal.valueOf(soraCoin))
                    val xorBalanceInEur =
                        asset.balance.transferable.multiply(BigDecimal.valueOf(soraCoin))

                    val needInXor =
                        if (asset.balance.transferable.greaterThan(xorRealRequiredBalance)) {
                            BigDecimal.ZERO
                        } else {
                            xorRequiredBalanceWithBacklash.minus(asset.balance.transferable)
                        }

                    val needInEur =
                        if (xorBalanceInEur.greaterThan(KYC_REAL_REQUIRED_BALANCE)) {
                            BigDecimal.ZERO
                        } else {
                            KYC_REQUIRED_BALANCE_WITH_BACKLASH.minus(xorBalanceInEur)
                        }

                    state.value = state.value.copy(
                        xorBalance = asset.balance.transferable,
                        enoughXor = asset.balance.transferable.greaterThan(
                            xorRealRequiredBalance
                        ),
                        percent = asset.balance.transferable.safeDivide(xorRealRequiredBalance),
                        needInXor = formatter.formatBigDecimal(needInXor, 5),
                        needInEur = formatter.formatBigDecimal(needInEur, 2),
                        xorRatioAvailable = true,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun subscribeSoraCardInfo() {
        viewModelScope.launch {
            walletInteractor.subscribeSoraCardInfo()
                .catch { onError(it) }
                .distinctUntilChanged()
                .collectLatest {
                    state.value = state.value.copy(soraCardInfo = it)
                }
        }
    }

    fun onEnableCard() {
        _launchSoraCardRegistration.value = createSoraCardContract(
            state.value.soraCardInfo?.let {
                SoraCardInfo(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    accessTokenExpirationTime = it.accessTokenExpirationTime
                )
            }
        )
    }

    fun onEuroIndicatorClick() {
        if (!state.value.xorRatioAvailable) {
            viewModelScope.launch {
                tryCatch {
                    xorEuro.value = blockExplorerManager.getXorPerEurRatio()
                }
            }
        }
    }

    fun onAlreadyHaveCard() {
        _launchSoraCardSignIn.value = createSoraCardContract(
            state.value.soraCardInfo?.let {
                SoraCardInfo(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    accessTokenExpirationTime = it.accessTokenExpirationTime
                )
            }
        )
    }

    fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        viewModelScope.launch {
            walletInteractor.updateSoraCardInfo(
                accessToken,
                refreshToken,
                accessTokenExpirationTime,
                kycStatus
            )
        }
    }

    fun onGetMoreXor() {
        state.value = state.value.copy(getMorXorAlert = true)
    }

    fun onDismissGetMoreXorAlert() {
        state.value = state.value.copy(getMorXorAlert = false)
    }

    fun onBuyCrypto() {
        assetsRouter.showBuyCrypto()
    }

    fun onSwap() {
        polkaswapRouter.showSwap(tokenToId = SubstrateOptionsProvider.feeAssetId)
    }

    fun onSeeBlacklist() {
        mainRouter.showWebView(
            title = resourceManager.getString(R.string.sora_card_blacklisted_countires_title),
            url = OptionsProvider.soraCardBlackList,
        )
    }
}
