/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.auth

import java.security.KeyPair

class AuthHolder {

    private var authKeypair: KeyPair? = null

    fun getKeyPair() = authKeypair

    fun setKeyPair(keyPair: KeyPair) {
        authKeypair = keyPair
    }
}