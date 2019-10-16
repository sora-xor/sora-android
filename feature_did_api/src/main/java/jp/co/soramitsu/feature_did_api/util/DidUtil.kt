/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_api.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jp.co.soramitsu.sora.sdk.crypto.common.HexdigestSaltGenerator
import jp.co.soramitsu.sora.sdk.crypto.json.Saltifier
import jp.co.soramitsu.sora.sdk.crypto.json.flattener.Flattener
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.did.model.dto.DID
import jp.co.soramitsu.sora.sdk.did.model.dto.authentication.Ed25519Sha3Authentication
import jp.co.soramitsu.sora.sdk.did.model.dto.publickey.Ed25519Sha3VerificationKey
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import java.io.IOException
import java.util.Date
import java.util.HashMap

object DidUtil {

    private val mapper = JsonUtil.buildMapper()
    private val flattener = Flattener()
    private val saltifier = Saltifier(mapper, HexdigestSaltGenerator())

    fun generateDID(identifier: String): DID {
        return DID.builder()
            .method("sora")
            .identifier(identifier)
            .build()
    }

    fun generateDDO(owner: DID, publicKey: ByteArray): DDO {
        return DDO.builder()
            .id(owner)
            .authentication(Ed25519Sha3Authentication(owner.withFragment("keys-1")))
            .created(Date())
            .publicKey(Ed25519Sha3VerificationKey(owner.withFragment("keys-1"), owner, publicKey))
            .build()
    }

    fun ddoToJson(ddo: DDO): String {
        return try {
            mapper.writeValueAsString(ddo)
        } catch (e: JsonProcessingException) {
            ""
        }
    }

    fun jsonToDdo(jsonDdo: String): DDO? {
        return try {
            mapper.readValue(jsonDdo, DDO::class.java)
        } catch (e: IOException) {
            null
        }
    }

    fun flatten(documentData: Map<String, String>): ObjectNode {
        return flattener.flatten(mapper.valueToTree<JsonNode>(documentData) as ObjectNode)
    }

    fun saltify(flattenedDocumentData: ObjectNode): JsonNode {
        return saltifier.saltify(flattenedDocumentData)
    }

    fun resaltify(flattenedDocumentData: JsonNode, salts: Map<*, *>): JsonNode {
        val resaltifiedDocument = mapper.createObjectNode()

        val iter = flattenedDocumentData.fields()

        while (iter.hasNext()) {
            val element = iter.next()
            val innerElement = mapper.createObjectNode()
            innerElement.set("v", element.value as JsonNode)
            innerElement.put("s", salts[element.key as String] as String?)

            resaltifiedDocument.set(element.key.toString(), innerElement)
        }
        return resaltifiedDocument
    }

    fun getSalts(objectNode: ObjectNode): Map<String, String> {
        val salts = HashMap<String, String>()
        val iter = objectNode.fields()

        while (iter.hasNext()) {
            val entry = iter.next()
            val key = entry.key as String
            val element = entry.value as JsonNode
            salts[key] = element.get("s").asText()
        }

        return salts
    }

    fun desaltify(saltifiedDocument: JsonNode): ObjectNode {
        return saltifier.desaltify(saltifiedDocument) as ObjectNode
    }

    fun areAccountsSame(accountId1: String, accountId2: String): Boolean {
        return removeDomainFromAccountId(accountId1) == removeDomainFromAccountId(accountId2)
    }

    fun removeDomainFromAccountId(accountId: String): String {
        return accountId.substring(0, accountId.indexOf("@"))
    }
}
