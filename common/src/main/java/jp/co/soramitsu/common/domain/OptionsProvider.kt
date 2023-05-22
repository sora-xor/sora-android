/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.BuildConfig

object OptionsProvider {
    var CURRENT_VERSION_CODE: Int = 0
    var CURRENT_VERSION_NAME: String = ""
    var APPLICATION_ID: String = ""
    val fileProviderAuthority: String get() = "$APPLICATION_ID.soraFileProvider"
    val header: String by lazy {
        "$APPLICATION_ID/$CURRENT_VERSION_NAME/$CURRENT_VERSION_CODE/${BuildConfig.BUILD_TYPE}/${BuildConfig.FLAVOR}"
    }

    const val substrate = "substrate"
    const val hexPrefix = "0x"
    const val defaultScale = 18
    const val nameByteLimit = 32
    const val fiatSymbol = "$"

    const val website = "https://sora.org"
    const val sourceLink = "https://github.com/sora-xor/Sora-Android"
    const val telegramLink = "https://t.me/sora_xor"
    const val telegramAnnouncementsLink = "https://t.me/sora_announcements"
    const val telegramHappinessLink = "https://t.me/sorahappiness"
    const val email = "support@sora.org"
    const val twitterLink = "https://twitter.com/sora_xor"
    const val youtubeLink = "https://youtube.com/sora_xor"
    const val instagramLink = "https://instagram.com/sora_xor"
    const val mediumLink = "https://medium.com/sora_xor"
    const val wikiLink = "https://wiki.sora.org"
    const val soraCardBlackList = "https://soracard.com/blacklist/"
}
