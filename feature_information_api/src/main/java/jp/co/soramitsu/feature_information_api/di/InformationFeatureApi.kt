/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_api.di

import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository

interface InformationFeatureApi {

    fun informationRepository(): InformationRepository
}