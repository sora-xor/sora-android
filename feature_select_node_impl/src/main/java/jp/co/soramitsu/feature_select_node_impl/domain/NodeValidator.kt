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

internal class NodeValidator @Inject constructor(private val inetAddressesWrapper: InetAddressesWrapper) {

    private companion object {
        val WS = Regex("^wss?:\\/\\/")

        const val PORT = "(?::\\d{5})"
        const val DNS =
            "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9][a-z0-9-]{0,61}[a-z0-9]"
        const val SEGMENT = "\\/[a-z0-9-_]+"

        val DNS_PATH_REGEX = Regex("^${DNS}$PORT?($SEGMENT)*/?\$")
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

        val isValid = if (address.count {
                it == ':'
            } > 1
        ) { // IPv4 address can't cotain more then one ":" symbol, but IPv6 should contain more
            if (address.contains("]")) { // IPv6 with port should be like "[host]:port"
                val addressPort = address.split("]:") // Split address for [host and port
                if (addressPort.size > 1) {
                    if (addressPort[1].toIntOrNull() != null) {
                        (
                            inetAddressesWrapper.isIpAddressValid(
                                addressPort.first().substring(1)
                            ) || DNS_PATH_REGEX.matches(
                                addressPort.first().substring(1)
                            )
                            ) && addressPort[1].toInt() > 0 && addressPort[1].toInt() <= 65535
                    } else {
                        false
                    }
                } else {
                    inetAddressesWrapper.isIpAddressValid(address) || DNS_PATH_REGEX.matches(address)
                }
            } else {
                inetAddressesWrapper.isIpAddressValid(address) || DNS_PATH_REGEX.matches(address)
            }
        } else {
            val addressPort = address.split(":")
            if (addressPort.size > 1) {
                if (addressPort[1].toIntOrNull() != null) {
                    (
                        inetAddressesWrapper.isIpAddressValid(addressPort.first()) || DNS_PATH_REGEX.matches(
                            addressPort.first()
                        )
                        ) && addressPort[1].toInt() > 0 && addressPort[1].toInt() <= 65535
                } else {
                    false
                }
            } else {
                inetAddressesWrapper.isIpAddressValid(address) || DNS_PATH_REGEX.matches(address)
            }
        }

        return isValid
    }
}
