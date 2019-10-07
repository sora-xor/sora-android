/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.common.util.mnemonic.MnemonicUtil
import org.junit.Assert
import org.junit.Test
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.util.encoders.Hex
import java.io.UnsupportedEncodingException

class CryptoTest {

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun scrypt_Test() {
        val expectedResult = "745731af4484f323968969eda289aeee005b5903ac561e64a5aca121797bf7734ef9fd58422e2e22183bcacba9ec87ba0c83b7a2e788f03ce0da06463433cda6"
        val password = "password"
        val salt = "salt"
        val N = 16384
        val r = 8
        val p = 1

        val actualResult = Hex.toHexString(
            SCrypt.generate(password.toByteArray(charset("UTF-8")), salt.toByteArray(charset("UTF-8")), N, r, p, 64)
        )
        Assert.assertEquals(expectedResult, actualResult)
    }

    @Test
    @Throws(Throwable::class)
    fun mnemonic_Test() {
        val mnemonic = "essence long fade naive almost board east unknown equip option oil version stone couple arch"
        val mnemonicBytes = MnemonicUtil.getBytesFromMnemonic(mnemonic)

        val seed = Crypto.generateScryptSeed(mnemonicBytes!!, "testProject", "test purpose", "testPassword")

        Assert.assertEquals(Hex.toHexString(seed!!), "a56b245dee3da0be3d566d1b80194e5547a330a986b2834e2019412a110b3638")
    }
}