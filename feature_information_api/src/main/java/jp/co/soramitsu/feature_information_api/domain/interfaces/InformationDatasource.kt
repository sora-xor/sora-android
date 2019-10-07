/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_api.domain.interfaces

import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer

interface InformationDatasource {

    fun saveInformationContent(content: List<InformationContainer>)

    fun retrieveInformationContent(): List<InformationContainer>?

    fun saveReputation(content: List<InformationContainer>)

    fun retrieveReputation(): List<InformationContainer>?

    fun saveSelectedCurrency(currency: Currency)

    fun retrieveSelectedCurrency(): Currency

    fun saveCurrencies(currencies: List<Currency>)

    fun retrieveCurrencies(selectedCurrency: Currency): List<Currency>?
}