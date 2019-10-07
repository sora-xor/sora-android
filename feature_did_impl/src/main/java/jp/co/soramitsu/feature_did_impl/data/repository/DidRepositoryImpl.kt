/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.util.Crypto
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidDatasource
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_did_api.util.DidUtil
import jp.co.soramitsu.feature_did_impl.data.model.DdoCompleteRequest
import jp.co.soramitsu.feature_did_impl.data.model.RetrieveDdoCompleteRequest
import jp.co.soramitsu.feature_did_impl.data.network.DidNetworkApi
import okhttp3.MediaType
import okhttp3.RequestBody
import org.spongycastle.util.encoders.Hex
import java.security.KeyPair
import java.util.Arrays
import javax.inject.Inject

class DidRepositoryImpl @Inject constructor(
    private val didApi: DidNetworkApi,
    private val didPrefs: DidDatasource,
    private val authHolder: AuthHolder
) : DidRepository {

    companion object {
        private const val PROJECT_NAME = "SORA"
        private const val PURPOSE = "iroha keypair"
    }

    override fun registerUserDdo(entropy: ByteArray): Completable {
        return buildDdoCompleteRequest(entropy)
            .flatMap { ddo ->
                didApi.postDdo(ddo.requestBody)
                    .map { ddo }
            }
            .doOnSuccess {
                didPrefs.saveDdo(it.userDdoSigned)
                didPrefs.saveKeys(it.keys)
                authHolder.setKeyPair(it.keys)
            }
            .ignoreElement()
    }

    private fun buildDdoCompleteRequest(entropy: ByteArray): Single<DdoCompleteRequest> {
        return Single.fromCallable {
            val seed = Crypto.generateScryptSeed(entropy, PROJECT_NAME, PURPOSE, "")
            val keys = Crypto.generateKeys(seed!!)

            val user = DidUtil.generateDID(Hex.toHexString(keys.public.encoded).substring(0, 20))

            val userDDO = DidUtil.generateDDO(user, keys.public.encoded)
            val userDdoSigned = Crypto.signDDO(keys, userDDO)

            val requestBody = RequestBody.create(MediaType.parse("application/json"), DidUtil.ddoToJson(userDdoSigned!!))

            DdoCompleteRequest(requestBody, userDdoSigned, keys)
        }
    }

    override fun retrieveUserDdo(entropy: ByteArray): Completable {
        return buildRetrieveDdoCompleteRequest(entropy)
            .flatMap { ddoRequest ->
                didApi.getDdo(ddoRequest.userDid)
                    .doOnSuccess { getDdoResponse ->
                        val ddo = DidUtil.jsonToDdo(getDdoResponse.ddo!!.toString())
                        val ddoPublicKey = Crypto.getProofKeyFromDdo(ddo!!)
                        if (Arrays.equals(ddoPublicKey, ddoRequest.keys.public.encoded)) {
                            didPrefs.saveKeys(ddoRequest.keys)
                            didPrefs.saveDdo(ddo)
                            authHolder.setKeyPair(ddoRequest.keys)
                        } else {
                            throw SoraException.businessError(ResponseCode.DID_NOT_FOUND)
                        }
                    }
            }
            .ignoreElement()
    }

    private fun buildRetrieveDdoCompleteRequest(entropy: ByteArray): Single<RetrieveDdoCompleteRequest> {
        return Single.fromCallable {
            val seed = Crypto.generateScryptSeed(entropy, PROJECT_NAME, PURPOSE, "")
            val keys = Crypto.generateKeys(seed!!)

            val user = DidUtil.generateDID(Hex.toHexString(keys.public.encoded).substring(0, 20))
            RetrieveDdoCompleteRequest(user.toString(), keys)
        }
    }

    override fun restoreAuth() {
        val keyPair = didPrefs.retrieveKeys()
        authHolder.setKeyPair(keyPair!!)
    }

    override fun saveMnemonic(mnemonic: String) {
        didPrefs.saveMnemonic(mnemonic)
    }

    override fun retrieveMnemonic(): Single<String> {
        return Single.just(didPrefs.retrieveMnemonic())
    }

    override fun retrieveKeypair(): Single<KeyPair> {
        return Single.just(didPrefs.retrieveKeys())
    }

    override fun retrieveDid(): Single<String> {
        return Single.fromCallable {
            val ddo = didPrefs.retrieveDdo()
            val did = ddo!!.id
            did.toString()
        }
    }

    override fun getIrohaUserName(): Single<String> {
        return retrieveDid()
            .map { it.replace(":", "_") }
    }

    override fun getAccountId(): Single<String> {
        return retrieveDid()
            .map { it.replace(":", "_") + "@sora" }
    }
}