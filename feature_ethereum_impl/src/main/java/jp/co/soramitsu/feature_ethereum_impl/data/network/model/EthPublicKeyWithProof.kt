package jp.co.soramitsu.feature_ethereum_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class EthPublicKeyWithProof(
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("signature") val keccakProof: KeccakProof
)
