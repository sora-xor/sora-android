package jp.co.soramitsu.common.data.did.network.model

import java.security.KeyPair

data class RetrieveDdoCompleteRequest(
    val userDid: String,
    val keys: KeyPair
)