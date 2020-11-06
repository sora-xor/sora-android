package jp.co.soramitsu.common.data.did.network.model

import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import okhttp3.RequestBody
import java.security.KeyPair

data class DdoCompleteRequest(
    val requestBody: RequestBody,
    val userDdoSigned: DDO,
    val keys: KeyPair
)