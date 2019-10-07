/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer

interface InformationRepository {

    fun getHelpContent(updateCached: Boolean): Single<List<InformationContainer>>

    fun getReputationContent(updateCached: Boolean): Single<List<InformationContainer>>

    fun getCurrencies(updateCached: Boolean): Single<List<Currency>>

    fun saveSelectedCurrency(currency: Currency): Completable

    fun getSelectedCurrency(): Single<Currency>
}