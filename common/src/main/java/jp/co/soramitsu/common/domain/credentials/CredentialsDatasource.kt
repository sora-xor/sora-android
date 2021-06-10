package jp.co.soramitsu.common.domain.credentials

import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import java.security.KeyPair

interface CredentialsDatasource {

    fun saveAddress(address: String)

    fun getAddress(): String

    fun saveKeys(keyPair: Keypair)

    fun retrieveKeys(): Keypair?

    fun saveMnemonic(mnemonic: String)

    fun retrieveMnemonic(): String

    fun saveIrohaKeys(keyPair: KeyPair)

    fun retrieveIrohaKeys(): KeyPair?

    fun saveIrohaAddress(address: String)

    fun getIrohaAddress(): String

    fun saveSignature(signature: ByteArray)

    fun retrieveSignature(): ByteArray
}
