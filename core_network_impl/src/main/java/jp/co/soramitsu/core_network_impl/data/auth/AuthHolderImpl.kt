/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.data.auth

import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import java.security.KeyPair
import javax.inject.Inject

class AuthHolderImpl @Inject constructor() : AuthHolder {

    private var authKeypair: KeyPair? = null

    override fun getKeyPair() = authKeypair

    override fun setKeyPair(keyPair: KeyPair) {
        authKeypair = keyPair
    }
}