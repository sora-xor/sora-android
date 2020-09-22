package jp.co.soramitsu.feature_account_api.domain.model

data class DeviceFingerPrint(
    val model: String,
    val osVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val language: String,
    val country: String,
    val timezone: Int
)