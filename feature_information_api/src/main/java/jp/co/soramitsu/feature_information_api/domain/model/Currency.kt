package jp.co.soramitsu.feature_information_api.domain.model

data class Currency(
    val code: String,
    val symbol: String,
    val name: String,
    val ratio: Float,
    var isSelected: Boolean
)