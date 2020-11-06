/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrefsInformationDatasourceTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var serializer: Serializer

    private lateinit var prefsInformationDatasource: PrefsInformationDatasource
    private val expectedJson = "{}"

    @Before fun setUp() {
        prefsInformationDatasource = PrefsInformationDatasource(preferences, serializer)
    }

    @Test fun `save information called`() {
        val keysInformation = "prefs_information"
        val list = mutableListOf(InformationContainer("title", "description"))
        given(serializer.serialize(list)).willReturn(expectedJson)

        prefsInformationDatasource.saveInformationContent(list)

        verify(preferences).putString(keysInformation, expectedJson)
    }

    @Test fun `retrieve information called`() {
        val keysInformation = "prefs_information"
        val list = mutableListOf(InformationContainer("title", "description"))
        given(serializer.deserialize<List<InformationContainer>>(expectedJson, object : TypeToken<List<InformationContainer>>() {}.type)).willReturn(list)
        given(preferences.getString(keysInformation)).willReturn(expectedJson)

        assertEquals(list, prefsInformationDatasource.retrieveInformationContent())
    }

    @Test fun `retrieve information called information empty`() {
        val keysInformation = "prefs_information"
        given(preferences.getString(keysInformation)).willReturn("")

        assertNull(prefsInformationDatasource.retrieveInformationContent())
    }

    @Test fun `save reputation called`() {
        val keysReputation = "reputation_content"
        val list = mutableListOf(InformationContainer("title", "description"))
        given(serializer.serialize(list)).willReturn(expectedJson)

        prefsInformationDatasource.saveReputation(list)

        verify(preferences).putString(keysReputation, expectedJson)
    }

    @Test fun `retrieve reputation called`() {
        val keysReputation = "reputation_content"
        val list = mutableListOf(InformationContainer("title", "description"))
        given(serializer.deserialize<List<InformationContainer>>(expectedJson, object : TypeToken<List<InformationContainer>>() {}.type)).willReturn(list)
        given(preferences.getString(keysReputation)).willReturn(expectedJson)

        assertEquals(list, prefsInformationDatasource.retrieveReputation())
    }

    @Test fun `retrieve reputation called reputation empty`() {
        val keysReputation = "reputation_content"
        given(preferences.getString(keysReputation)).willReturn("")

        assertNull(prefsInformationDatasource.retrieveReputation())
    }

    @Test fun `save selected currency called`() {
        val keySelectedCurrency = "prefs_selected_currency"
        val currency = Currency("USD", "$", "USD", 2f, true)
        given(serializer.serialize(currency)).willReturn(expectedJson)

        prefsInformationDatasource.saveSelectedCurrency(currency)

        verify(preferences).putString(keySelectedCurrency, expectedJson)
    }

    @Test fun `retrieve selected currency called`() {
        val keySelectedCurrency = "prefs_selected_currency"
        val currency = Currency("USD", "$", "USD", 2f, true)
        given(preferences.getString(keySelectedCurrency)).willReturn(expectedJson)
        given(serializer.deserialize(expectedJson, Currency::class.java)).willReturn(currency)

        assertEquals(currency, prefsInformationDatasource.retrieveSelectedCurrency())
    }

    @Test fun `retrieve selected currency called with no cached currency`() {
        val keySelectedCurrency = "prefs_selected_currency"
        val resultedCurrency = Currency("USD", "$", "US Dollars", 1f, true)
        given(preferences.getString(keySelectedCurrency)).willReturn("")

        assertEquals(resultedCurrency, prefsInformationDatasource.retrieveSelectedCurrency())
    }

    @Test fun `save currencies called`() {
        val keyCurrencies = "prefs_currency"
        val currencies = mutableListOf(Currency("USD", "$", "USD", 2f, true), Currency("RUB", "P", "RUB", 2f, false))
        given(serializer.serialize(currencies)).willReturn(expectedJson)

        prefsInformationDatasource.saveCurrencies(currencies)

        verify(preferences).putString(keyCurrencies, expectedJson)
    }

    @Test fun `retrieve currencies called`() {
        val keyCurrencies = "prefs_currency"
        val currencies = mutableListOf(Currency("USD", "$", "USD", 2f, false), Currency("RUB", "P", "RUB", 2f, false))
        val currenciesWithSelected = mutableListOf(Currency("USD", "$", "USD", 2f, true), Currency("RUB", "P", "RUB", 2f, false))
        given(preferences.getString(keyCurrencies)).willReturn(expectedJson)
        given(serializer.deserialize<List<Currency>>(expectedJson, object : TypeToken<List<Currency>>() {}.type)).willReturn(currencies)

        assertEquals(currenciesWithSelected, prefsInformationDatasource.retrieveCurrencies(currencies[0]))
    }

    @Test fun `retrieve currencies called if no cached`() {
        val keyCurrencies = "prefs_currency"
        given(preferences.getString(keyCurrencies)).willReturn("")

        assertNull(prefsInformationDatasource.retrieveCurrencies(Currency("", "", "", 2f, false)))
    }
}
