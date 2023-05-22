/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import java.util.Locale
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardInfo
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData

fun createSoraCardContract(soraCardInfo: SoraCardInfo?): SoraCardContractData {
    return SoraCardContractData(
        locale = Locale.ENGLISH,
        apiKey = BuildConfig.SORA_CARD_API_KEY,
        domain = BuildConfig.SORA_CARD_DOMAIN,
        environment = when {
            BuildUtils.isFlavors(Flavor.PROD) -> SoraCardEnvironmentType.PRODUCTION
            else -> SoraCardEnvironmentType.TEST
        },
        soraCardInfo = soraCardInfo,
        kycCredentials = SoraCardKycCredentials(
            endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
            username = BuildConfig.SORA_CARD_KYC_USERNAME,
            password = BuildConfig.SORA_CARD_KYC_PASSWORD,
        ),
        client = OptionsProvider.header,
    )
}
