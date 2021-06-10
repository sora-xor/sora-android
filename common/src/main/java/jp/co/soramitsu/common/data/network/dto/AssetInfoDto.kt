package jp.co.soramitsu.common.data.network.dto

data class AssetInfoDto(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isMintable: Boolean,
)
