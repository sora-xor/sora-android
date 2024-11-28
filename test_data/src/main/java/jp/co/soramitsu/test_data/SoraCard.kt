/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.test_data

import java.util.Locale
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardBasicStatus
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardBasicContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardFlow
import jp.co.soramitsu.oauth.uiscreens.clientsui.UiStyle

object SoraCardTestData {

    val soraCardBasicStatusTest = SoraCardBasicStatus(
        initialized = false,
        initError = null,
        availabilityInfo = null,
        verification = SoraCardCommonVerification.NotFound,
        needInstallUpdate = false,
        applicationFee = null,
        ibanInfo = null,
        phone = null,
    )

    private val SORA_CARD_BASIC_CONTRACT = SoraCardBasicContractData(
        apiKey = BuildConfig.SORA_CARD_API_KEY,
        domain = BuildConfig.SORA_CARD_DOMAIN,
        environment = SoraCardEnvironmentType.TEST,
        recaptcha = BuildConfig.SORA_CARD_RECAPTCHA,
        platform = BuildConfig.SORA_CARD_PLATFORM,
    )

    val SORA_CARD_CONTRACT_DATA = SoraCardContractData(
        basic = SORA_CARD_BASIC_CONTRACT,
        locale = Locale.ENGLISH,
        client = "test android client",
        soraBackEndUrl = "soracard backend",
        clientDark = false,
        flow = SoraCardFlow.SoraCardKycFlow(
            kycCredentials = SoraCardKycCredentials(
                endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
                username = BuildConfig.SORA_CARD_KYC_USERNAME,
                password = BuildConfig.SORA_CARD_KYC_PASSWORD,
            ),
            areAttemptsPaidSuccessfully = false,
            isEnoughXorAvailable = false,
            isIssuancePaid = false,
            userAvailableXorAmount = 0.0,
            logIn = false,
        ),
        clientCase = UiStyle.SW,
    )

    val registrationLauncher = SoraCardContractData(
        basic = SORA_CARD_BASIC_CONTRACT,
        locale = Locale.ENGLISH,
        client = "test android client",
        soraBackEndUrl = "soracard backend",
        clientDark = false,
        flow = SoraCardFlow.SoraCardKycFlow(
            kycCredentials = SoraCardKycCredentials(
                endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
                username = BuildConfig.SORA_CARD_KYC_USERNAME,
                password = BuildConfig.SORA_CARD_KYC_PASSWORD,
            ),
            areAttemptsPaidSuccessfully = false,
            isEnoughXorAvailable = false,
            isIssuancePaid = false,
            userAvailableXorAmount = 0.0,
            logIn = false,
        ),
        clientCase = UiStyle.SW,
    )

    val signInLauncher = SoraCardContractData(
        basic = SORA_CARD_BASIC_CONTRACT,
        locale = Locale.ENGLISH,
        client = "test android client",
        soraBackEndUrl = "soracard backend",
        clientDark = false,
        flow = SoraCardFlow.SoraCardKycFlow(
            areAttemptsPaidSuccessfully = false,
            isEnoughXorAvailable = false,
            isIssuancePaid = false,
            kycCredentials = SoraCardKycCredentials(
                endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
                username = BuildConfig.SORA_CARD_KYC_USERNAME,
                password = BuildConfig.SORA_CARD_KYC_PASSWORD,
            ),
            userAvailableXorAmount = 0.0,
            logIn = false,
        ),
        clientCase = UiStyle.SW,

    )
}
