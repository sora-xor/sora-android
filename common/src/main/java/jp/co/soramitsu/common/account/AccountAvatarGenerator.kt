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

package jp.co.soramitsu.common.account

import android.graphics.drawable.PictureDrawable
import com.caverock.androidsvg.SVG
import javax.inject.Singleton
import jdenticon.Jdenticon
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId

@Singleton
class AccountAvatarGenerator(
    private val resourceManager: ResourceManager,
) {
    private fun createAvatarFromKey(publicKey: String, sizeInDp: Int): PictureDrawable {
        val icon = Jdenticon.toSvg(publicKey, resourceManager.dp2px(sizeInDp))
        val svg = SVG.getFromString(icon)
        return PictureDrawable(svg.renderToPicture())
    }

    fun createAvatar(address: String, sizeInDp: Int): PictureDrawable =
        createAvatarFromKey(publicKey = address.toAccountId().toHexString(false), sizeInDp = sizeInDp)
}
