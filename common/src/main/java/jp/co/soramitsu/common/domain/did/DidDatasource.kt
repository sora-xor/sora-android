/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain.did

import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import java.security.KeyPair

interface DidDatasource {

    fun saveKeys(keyPair: KeyPair)

    fun retrieveKeys(): KeyPair?

    fun saveDdo(ddo: DDO)

    fun retrieveDdo(): DDO?

    fun saveMnemonic(mnemonic: String)

    fun retrieveMnemonic(): String
}