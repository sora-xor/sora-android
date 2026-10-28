/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.BuildConfig

object OptionsProvider {
    var CURRENT_VERSION_CODE: Int = 0
    var CURRENT_VERSION_NAME: String = ""
    var APPLICATION_ID: String = ""
    const val configCommon = "https://config.polkaswap2.io/${FlavorOptionsProvider.typesFilePath}/common.json"
    const val configMobile = "https://config.polkaswap2.io/${FlavorOptionsProvider.typesFilePath}/mobile.json"
    const val polkaswapIndexerEndpoint = "https://pi.soramitsu.io/graphql"
    const val blockExplorerUrl = "https://sorametrics.org"
    val fileProviderAuthority: String get() = "$APPLICATION_ID.soraFileProvider"
    val header: String by lazy {
        "$APPLICATION_ID/$CURRENT_VERSION_NAME/$CURRENT_VERSION_CODE/${BuildConfig.BUILD_TYPE}/${BuildConfig.FLAVOR}"
    }

    const val substrate = "substrate"
    const val defaultScale = 18
    const val nameByteLimit = 32
    const val fiatSymbol = "$"
    const val nbspace = Typography.nbsp

    const val website = "https://sora.org"
    const val sourceLink = "https://github.com/sora-xor/Sora-Android"
    const val telegramLink = "https://t.me/sora_xor"
    const val telegramAnnouncementsLink = "https://t.me/sora_announcements"
    const val telegramHappinessLink = "https://t.me/sora_happy"
    const val email = "support@sora.org"
    const val twitterLink = "https://twitter.com/sora_xor"
    const val youtubeLink = "https://youtube.com/sora_xor"
    const val instagramLink = "https://instagram.com/sora_xor"
    const val mediumLink = "https://medium.com/sora-xor"
    const val wikiLink = "https://wiki.sora.org"
}
