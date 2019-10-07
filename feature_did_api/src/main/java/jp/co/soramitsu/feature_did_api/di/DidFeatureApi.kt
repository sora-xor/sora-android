/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_api.di

import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository

interface DidFeatureApi {

    fun providesDidRepository(): DidRepository
}