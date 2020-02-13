/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.di

import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface OnboardingFeatureDependencies {

    fun userRepository(): UserRepository

    fun didRepository(): DidRepository

    fun mainStarter(): MainStarter

    fun walletRepository(): WalletRepository

    fun invitationHandler(): InvitationHandler

    fun debounceClickHandler(): DebounceClickHandler
}