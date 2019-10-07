/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_api.data.auth

import java.security.KeyPair

interface AuthHolder {

    fun getKeyPair(): KeyPair?

    fun setKeyPair(keyPair: KeyPair)
}