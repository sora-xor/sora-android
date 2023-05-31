/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
