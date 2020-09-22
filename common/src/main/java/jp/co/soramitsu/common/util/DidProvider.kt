/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Single
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.did.model.dto.DID
import jp.co.soramitsu.sora.sdk.did.model.dto.authentication.Ed25519Sha3Authentication
import jp.co.soramitsu.sora.sdk.did.model.dto.publickey.Ed25519Sha3VerificationKey
import java.io.IOException
import java.util.Date

class DidProvider(
    private val objectMapper: ObjectMapper
) {

    fun generateDID(identifier: String): DID {
        return DID.builder()
            .method("sora")
            .identifier(identifier)
            .build()
    }

    fun generateDDO(owner: DID, publicKey: ByteArray): Single<DDO> {
        return Single.fromCallable {
            DDO.builder()
                .id(owner)
                .authentication(Ed25519Sha3Authentication(owner.withFragment("keys-1")))
                .created(Date())
                .publicKey(Ed25519Sha3VerificationKey(owner.withFragment("keys-1"), owner, publicKey))
                .build()
        }
    }

    fun ddoToJson(ddo: DDO): String {
        return try {
            objectMapper.writeValueAsString(ddo)
        } catch (e: JsonProcessingException) {
            ""
        }
    }

    fun jsonToDdo(jsonDdo: String): DDO? {
        return try {
            objectMapper.readValue(jsonDdo, DDO::class.java)
        } catch (e: IOException) {
            null
        }
    }
}