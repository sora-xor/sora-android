/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.json_decoder

class JsonAccountData(
    val accounts: List<Account>,
    val encoded: String,
    val encoding: Encoding = Encoding(),
) {
    companion object {
        private const val BATCH_PKCS = "batch-pkcs8"
        private const val SCRYPT = "scrypt"
        private const val XSALSA20_POLY1305 = "xsalsa20-poly1305"
        private const val VERSION = "3"
    }

    data class Encoding(
        val content: List<String> = listOf(BATCH_PKCS),
        val type: List<String> = listOf(SCRYPT, XSALSA20_POLY1305),
        val version: String = VERSION
    )

    data class Account(
        val address: String,
        val meta: Meta
    )

    data class Meta(
        val name: String?,
        val genesisHash: String,
        val whenCreated: Long = System.currentTimeMillis()
    )
}
