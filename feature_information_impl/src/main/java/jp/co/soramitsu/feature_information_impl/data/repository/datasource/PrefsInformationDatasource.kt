/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.data.repository.datasource

import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationDatasource
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import javax.inject.Inject

class PrefsInformationDatasource @Inject constructor(
    private val preferences: Preferences,
    private val serializer: Serializer
) : InformationDatasource {

    companion object {
        private const val PREFS_SELECTED_CURRENCY = "prefs_selected_currency"
        private const val PREFS_CURRENCY = "prefs_currency"
        private const val PREFS_INFORMATION = "prefs_information"
        private const val PREFS_REPUTATION = "reputation_content"
    }

    override fun saveInformationContent(content: List<InformationContainer>) {
        preferences.putString(PREFS_INFORMATION, serializer.serialize(content))
    }

    override fun retrieveInformationContent(): List<InformationContainer>? {
        val informationJson = preferences.getString(PREFS_INFORMATION)

        return if (informationJson.isEmpty()) {
            null
        } else {
            try {
                serializer.deserialize<List<InformationContainer>>(informationJson, object : TypeToken<List<InformationContainer>>() {}.type)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
    }

    override fun saveReputation(content: List<InformationContainer>) {
        preferences.putString(PREFS_REPUTATION, serializer.serialize(content))
    }

    override fun retrieveReputation(): List<InformationContainer>? {
        val informationJson = preferences.getString(PREFS_REPUTATION)

        return if (informationJson.isEmpty()) {
            null
        } else {
            try {
                serializer.deserialize<List<InformationContainer>>(informationJson, object : TypeToken<List<InformationContainer>>() {}.type)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
    }

    override fun saveSelectedCurrency(currency: Currency) {
        preferences.putString(PREFS_SELECTED_CURRENCY, serializer.serialize(currency))
    }

    override fun retrieveSelectedCurrency(): Currency {
        val selectedCurrencyString = preferences.getString(PREFS_SELECTED_CURRENCY)

        return if (selectedCurrencyString.isEmpty()) {
            Currency("USD", "$", "US Dollars", 1f, true)
        } else {
            serializer.deserialize(selectedCurrencyString, Currency::class.java)
        }
    }

    override fun saveCurrencies(currencies: List<Currency>) {
        preferences.putString(PREFS_CURRENCY, serializer.serialize(currencies))
    }

    override fun retrieveCurrencies(currency: Currency): List<Currency>? {
        val currenciesJsonString = preferences.getString(PREFS_CURRENCY)

        return if (currenciesJsonString.isEmpty()) {
            null
        } else {
            try {
                serializer.deserialize<List<Currency>>(currenciesJsonString, object : TypeToken<List<Currency>>() {}.type)
            } catch (e: JsonSyntaxException) {
                null
            }
        }?.map { it.apply { isSelected = code == currency.code } }
    }
}