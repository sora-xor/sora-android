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

package jp.co.soramitsu.feature_ethereum_impl.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.math.BigInteger
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class Bip3244Test {

    private val mnemonic =
        "agent plunge forget key stadium bridge garlic board acid volcano patrol sick"
    private val hexseed =
        "e175fb39d2f2688495d74462ea2596c3fe66a1213a72acd74021c8ac145e7addfe8f9c4c4ce12ec2a03adad3f86cbbb11c33efb51d44f84a6b82c7a5eb65d233"
    private val privateKey =
        BigInteger("42478643bce06b7d98415ab869afc109e49ade02dcc1dfef7b5ca47ce799b6fe", 16)
    private val address = "0x736d66B3313BD8c7d91a08423a0354E4EF57219b"
    private val publicKey = BigInteger(
        "a499871e2f0ed30cfd2ba035f35e7cccf094d01b7207e275d86dcdf538e41a512dfef76822fc747eafe3cd825b1a05d3ecd0e995acfd9e76edaaab53baf8c198",
        16
    )

    @Test
    fun `bip32 and 44 protocols are implemented right`() {
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            60 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0,
            0
        )
        val seed = MnemonicUtils.generateSeed(mnemonic, "")

        assertEquals(Hex.toHexString(seed), hexseed)

        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val bip44Keypair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path)
        val credentials = Credentials.create(bip44Keypair)

        assertEquals(publicKey, credentials.ecKeyPair.publicKey)
        assertEquals(privateKey, credentials.ecKeyPair.privateKey)
        assertEquals(address.lowercase(Locale.getDefault()), credentials.address)
    }
}
