/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_account_impl.data.network.model.CountryRemote

fun mapCountryDtoToCounty(countryRemote: CountryRemote): List<Country> {
    val countries = mutableListOf<Country>()
    val iter = countryRemote.topics.entrySet().iterator()
    while (iter.hasNext()) {
        val key = iter.next()
        val value = countryRemote.topics.get(key.key).asJsonObject
        countries.add(
            Country(
                key.key,
                value.getAsJsonPrimitive("name").asString,
                value.getAsJsonPrimitive("dial_code").asString
            )
        )
    }
    return countries
}