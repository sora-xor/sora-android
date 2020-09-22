/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface WalletFeatureApi {

    fun providesWalletRepository(): WalletRepository
}