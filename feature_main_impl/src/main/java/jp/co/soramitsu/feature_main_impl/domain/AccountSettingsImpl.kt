/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import java.security.KeyPair

class AccountSettingsImpl(
    private val didRepository: DidRepository
) : AccountSettings {

    override fun getKeyPair(): Single<KeyPair> {
        return didRepository.retrieveKeypair()
    }

    override fun getAccountId(): Single<String> {
        return didRepository.getAccountId()
    }

    override fun mnemonic(): Single<String> {
        return didRepository.retrieveMnemonic()
    }
}