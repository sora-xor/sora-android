package jp.co.soramitsu.feature_information_impl.data.mappers

import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_information_impl.data.network.model.InformationRemote

private const val TITLE = "title"
private const val DESCRIPTION = "description"
private const val CODE = "code"
private const val SYMBOL = "symbol"
private const val NAME = "name"
private const val RATIO = "ratio"

fun mapInformationRemoteToInformationList(informationRemote: InformationRemote?): List<InformationContainer> {
    val informations = mutableListOf<InformationContainer>()

    if (informationRemote == null)
        return informations

    val iter = informationRemote.topics.entrySet().iterator()
    while (iter.hasNext()) {
        val key = iter.next()
        val value = informationRemote.topics.get(key.key).asJsonObject
        informations.add(
            InformationContainer(
                value.getAsJsonPrimitive(TITLE).asString,
                value.getAsJsonPrimitive(DESCRIPTION).asString
            )
        )
    }

    return informations
}

fun mapInformationRemoteToCurrenciesList(informationRemote: InformationRemote?, selectedCurrencyCode: String): List<Currency> {
    val currencies = mutableListOf<Currency>()

    if (informationRemote == null)
        return currencies

    val iter = informationRemote.topics.entrySet().iterator()
    while (iter.hasNext()) {
        val key = iter.next()
        val value = informationRemote.topics.get(key.key).asJsonObject
        currencies.add(
            Currency(
                value.getAsJsonPrimitive(CODE).asString,
                value.getAsJsonPrimitive(SYMBOL).asString,
                value.getAsJsonPrimitive(NAME).asString,
                value.getAsJsonPrimitive(RATIO).asFloat,
                selectedCurrencyCode == value.getAsJsonPrimitive(CODE).asString
            )
        )
    }

    return currencies
}