/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_api.di

import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter

interface MultiaccountFeatureApi {

    fun provideMultiaccountStarter(): MultiaccountStarter
}
