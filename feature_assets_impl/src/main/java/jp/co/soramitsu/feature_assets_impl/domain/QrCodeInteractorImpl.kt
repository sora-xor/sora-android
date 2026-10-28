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

package jp.co.soramitsu.feature_assets_impl.domain

import java.util.StringJoiner
import javax.inject.Inject
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.util.QrException
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.QrCodeInteractor
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider

class QrCodeInteractorImpl @Inject constructor(
    private val assetsRepository: AssetsRepository,
    private val userRepository: UserRepository,
    private val runtimeManager: RuntimeManager,
) : QrCodeInteractor {

    override suspend fun createQrInput(
        userAddress: String,
        userPublicKey: String,
        userName: String,
        tokenId: String?,
        amount: String?
    ) = with(StringJoiner(QR_CODE_INPUT_DELIMITER)) {
        add(OptionsProvider.substrate)
        add(userAddress)
        add(userPublicKey)
        add(userName)
        add(tokenId ?: SubstrateOptionsProvider.feeAssetId)
        if (amount != null)
            add(amount)

        return@with this.toString()
    }

    override suspend fun processQrResult(
        qrCodeDecodingResult: String
    ): Triple<String, String, String?> {
        val parsedQrCode = parseQROutput(qrCodeDecodingResult)

        if (parsedQrCode.size != 5 && parsedQrCode.size != 6)
            throw QrException.decodeError()

        if (parsedQrCode.getOrNull(0) != OptionsProvider.substrate)
            throw QrException.decodeError()

        val address = parsedQrCode.getOrNull(1).run {
            if (this == null)
                throw QrException.decodeError()

            val myAddress = userRepository.getCurSoraAccount()
                .substrateAddress

            if (this == myAddress)
                throw QrException.sendingToMyselfError()

            val ok = runtimeManager.isAddressOk(
                address = this
            )

            if (!ok) throw QrException.userNotFoundError()

            return@run this
        }

        val tokenId = parsedQrCode.getOrNull(4).run {
            if (this == null)
                throw QrException.decodeError()

            val isTokenInWhitelist = assetsRepository.isWhitelistedToken(
                tokenId = this
            )

            return@run if (isTokenInWhitelist) this else
                SubstrateOptionsProvider.feeAssetId
        }

        val amount = parsedQrCode.getOrNull(5)

        return Triple(address, tokenId, amount)
    }

    private fun parseQROutput(
        qrCodeDecoderOutput: String
    ) = qrCodeDecoderOutput.split(QR_CODE_INPUT_DELIMITER)

    private companion object {
        const val QR_CODE_INPUT_DELIMITER = ":"
    }
}
