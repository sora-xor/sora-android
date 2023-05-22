/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_data

import java.util.Locale
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardInfo
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData

object SoraCardTestData {

    val SORA_CARD_CONTRACT_DATA = SoraCardContractData(
        locale = Locale.ENGLISH,
        apiKey = BuildConfig.SORA_CARD_API_KEY,
        domain = BuildConfig.SORA_CARD_DOMAIN,
        environment = when {
            BuildUtils.isFlavors(Flavor.PROD) -> SoraCardEnvironmentType.PRODUCTION
            else -> SoraCardEnvironmentType.TEST
        },
        soraCardInfo = SoraCardInfo(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            accessTokenExpirationTime = Long.MAX_VALUE
        ),
        client = "test android client",
        kycCredentials = SoraCardKycCredentials(
            endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
            username = BuildConfig.SORA_CARD_KYC_USERNAME,
            password = BuildConfig.SORA_CARD_KYC_PASSWORD,
        ),
    )

    val SORA_CARD_INFO = jp.co.soramitsu.common.domain.SoraCardInformation(
        id = "soraCardId",
        accessToken = "accessToken",
        refreshToken = "refreshToken",
        accessTokenExpirationTime = Long.MAX_VALUE,
        kycStatus = "Completed"
    )

    val registrationLauncher = SoraCardContractData(
        locale = Locale.ENGLISH,
        apiKey = BuildConfig.SORA_CARD_API_KEY,
        domain = BuildConfig.SORA_CARD_DOMAIN,
        kycCredentials = SoraCardKycCredentials(
            endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
            username = BuildConfig.SORA_CARD_KYC_USERNAME,
            password = BuildConfig.SORA_CARD_KYC_PASSWORD,
        ),
        environment = when {
            BuildUtils.isFlavors(Flavor.PROD) -> SoraCardEnvironmentType.PRODUCTION
            else -> SoraCardEnvironmentType.TEST
        },
        soraCardInfo = SoraCardInfo(
            accessToken = SORA_CARD_INFO.accessToken,
            refreshToken = SORA_CARD_INFO.refreshToken,
            accessTokenExpirationTime = SORA_CARD_INFO.accessTokenExpirationTime
        ),
        client = "test android client",
    )

    val signInLauncher = SoraCardContractData(
        locale = Locale.ENGLISH,
        apiKey = BuildConfig.SORA_CARD_API_KEY,
        domain = BuildConfig.SORA_CARD_DOMAIN,
        environment = when {
            BuildUtils.isFlavors(Flavor.PROD) -> SoraCardEnvironmentType.PRODUCTION
            else -> SoraCardEnvironmentType.TEST
        },
        soraCardInfo = SoraCardInfo(
            accessToken = SORA_CARD_INFO.accessToken,
            refreshToken = SORA_CARD_INFO.refreshToken,
            accessTokenExpirationTime = SORA_CARD_INFO.accessTokenExpirationTime,
        ),
        client = "test android client",
        kycCredentials = SoraCardKycCredentials(
            endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
            username = BuildConfig.SORA_CARD_KYC_USERNAME,
            password = BuildConfig.SORA_CARD_KYC_PASSWORD,
        ),
    )
}
