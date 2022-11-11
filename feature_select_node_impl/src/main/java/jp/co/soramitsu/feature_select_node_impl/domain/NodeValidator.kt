/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.domain

import javax.inject.Inject

internal sealed class ValidationEvent {

    object Succeed : ValidationEvent()
    object ProtocolValidationFailed : ValidationEvent()
    object AddressValidationFailed : ValidationEvent()
}

internal class NodeValidator @Inject constructor() {

    private companion object {
        val WS = Regex("^wss?:\\/\\/")

        const val PORT = "(?::\\d{5})"
        const val DNS = "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9][a-z0-9-]{0,61}[a-z0-9]"
        const val SEGMENT = "\\/[a-z0-9-_]+"

        val DNS_PATH_REGEX = Regex("^${DNS}$PORT?($SEGMENT)*/?\$")

        const val IPV4_PART = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]\\d|\\d)"
        const val IPV4 = "$IPV4_PART(?:\\.$IPV4_PART){3}"

        val IPV4_REGEX = Regex("^${IPV4}$PORT?($SEGMENT)*/\$")
    }

    fun validate(url: String): ValidationEvent {
        if (!checkProtocol(url)) {
            return ValidationEvent.ProtocolValidationFailed
        }

        if (!checkAddress(url)) {
            return ValidationEvent.AddressValidationFailed
        }

        return ValidationEvent.Succeed
    }

    private fun checkProtocol(url: String): Boolean = WS.containsMatchIn(url)

    private fun checkAddress(url: String): Boolean {
        val address = url.replace(WS, "")

        return DNS_PATH_REGEX.matches(address) || IPV4_REGEX.matches(address)
    }
}
