/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_api.di

import jp.co.soramitsu.feature_main_api.launcher.MainStarter

interface MainFeatureApi {

    fun provideMainStarter(): MainStarter
}