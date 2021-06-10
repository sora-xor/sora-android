/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.mappers

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import java.math.BigInteger

class EthereumCredentialsMapper {

    companion object {
        private const val hexRadix = 16
    }

    fun getPublicKey(privateKey: BigInteger): BigInteger {
        return Credentials.create(privateKey.toString(hexRadix)).ecKeyPair.publicKey
    }

    fun getAddress(privateKey: BigInteger): String {
        return Credentials.create(privateKey.toString(hexRadix)).address
    }

    fun getCredentials(privateKey: BigInteger): Credentials {
        return Credentials.create(privateKey.toString(hexRadix))
    }

    fun getCredentialsFromECKeyPair(ecKeyPair: Bip32ECKeyPair): Credentials {
        return Credentials.create(ecKeyPair)
    }
}
