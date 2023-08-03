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
    const val mediumLink = "https://medium.com/sora-xor"
    const val wikiLink = "https://wiki.sora.org"
    const val soraCardBlackList = "https://soracard.com/blacklist/"
}
