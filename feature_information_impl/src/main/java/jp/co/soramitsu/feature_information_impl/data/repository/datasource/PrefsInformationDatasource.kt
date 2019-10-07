/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.data.repository.datasource

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationDatasource
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import javax.inject.Inject

class PrefsInformationDatasource @Inject constructor(
    private val prefsUtl: PrefsUtil
) : InformationDatasource {

    companion object {
        private const val PREFS_SELECTED_CURRENCY = "prefs_selected_currency"
        private const val PREFS_CURRENCY = "prefs_currency"
        private const val PREFS_INFORMATION = "prefs_information"
        private const val PREFS_REPUTATION = "reputation_content"
    }

    private val gson = Gson()

    override fun saveInformationContent(content: List<InformationContainer>) {
        prefsUtl.putString(PREFS_INFORMATION, gson.toJson(content))
    }

    override fun retrieveInformationContent(): List<InformationContainer>? {
        val informationJson = prefsUtl.getString(PREFS_INFORMATION)

        return if (informationJson.isEmpty()) {
            null
        } else {
            try {
                gson.fromJson<List<InformationContainer>>(informationJson, object : TypeToken<List<InformationContainer>>() {}.type)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
    }

    override fun saveReputation(content: List<InformationContainer>) {
        prefsUtl.putString(PREFS_REPUTATION, gson.toJson(content))
    }

    override fun retrieveReputation(): List<InformationContainer>? {
        val informationJson = prefsUtl.getString(PREFS_REPUTATION)

        return if (informationJson.isEmpty()) {
            null
        } else {
            try {
                gson.fromJson<List<InformationContainer>>(informationJson, object : TypeToken<List<InformationContainer>>() {}.type)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
    }

    override fun saveSelectedCurrency(currency: Currency) {
        prefsUtl.putString(PREFS_SELECTED_CURRENCY, gson.toJson(currency))
    }

    override fun retrieveSelectedCurrency(): Currency {
        val selectedCurrencyString = prefsUtl.getString(PREFS_SELECTED_CURRENCY)

        return if (selectedCurrencyString.isEmpty()) {
            Currency("USD", "$", "US Dollars", 1f, true)
        } else {
            gson.fromJson(prefsUtl.getString(selectedCurrencyString), Currency::class.java)
        }
    }

    override fun saveCurrencies(currencies: List<Currency>) {
        prefsUtl.putString(PREFS_CURRENCY, gson.toJson(currencies))
    }

    override fun retrieveCurrencies(currency: Currency): List<Currency>? {
        val currenciesJsonString = prefsUtl.getString(PREFS_CURRENCY)

        return if (currenciesJsonString.isEmpty()) {
            null
        } else {
            try {
                gson.fromJson<List<Currency>>(currenciesJsonString, object : TypeToken<List<Currency>>() {}.type)
            } catch (e: JsonSyntaxException) {
                null
            }
        }?.map { it.apply { isSelected = code == currency.code } }
    }
}