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

package jp.co.soramitsu.feature_assets_impl.domain

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.util.QrException
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.QrCodeInteractor
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given

@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class QrCodeInteractorTest {

    private val soraAccount = SoraAccount("address", "name")

    private lateinit var qrCodeInteractor: QrCodeInteractor

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Before
    fun init() = runTest {
        given(
            methodCall = userRepository.getCurSoraAccount()
        ).willReturn(soraAccount)

        QrCodeInteractorImpl(
            assetsRepository = assetsRepository,
            userRepository = userRepository,
            runtimeManager = runtimeManager
        ).apply { qrCodeInteractor = this }
    }

    @Test
    fun `process qr called`() = runTest {
        val content = "substrate:notMyAddress:en:part4:part5"

        given(
            methodCall = runtimeManager
                .isAddressOk("notMyAddress")
        ).willReturn(true)

        given(
            methodCall = assetsRepository
                .isWhitelistedToken("part5")
        ).willReturn(true)

        val result = runCatching {
            qrCodeInteractor.processQrResult(content)
        }

        Assert.assertTrue(
            result.isSuccess
        )

        Assert.assertTrue(
            result.getOrThrow() == Triple(
                "notMyAddress",
                "part5",
                null
            )
        )
    }

    @Test
    fun `process qr called with wrong qr data`() = runTest {
        val content = "substrate:notMyAddress:en:tjj:qwe"

        given(
            methodCall = runtimeManager.isAddressOk(
                address = "notMyAddress"
            )
        ).willReturn(false)

        val result = runCatching {
            qrCodeInteractor.processQrResult(content)
        }

        Assert.assertTrue(
            result.isFailure
        )

        val exception = result.exceptionOrNull()!!
        Assert.assertTrue(
            exception is QrException &&
                exception.kind == QrException.Kind.USER_NOT_FOUND
        )
    }

    @Test
    fun `process qr called with users qr data`() = runTest {
        val content = "substrate:address:en:tjj:qwe"

        val result = runCatching {
            qrCodeInteractor.processQrResult(content)
        }

        Assert.assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()!!

        Assert.assertTrue(
            exception is QrException &&
                exception.kind == QrException.Kind.SENDING_TO_MYSELF
        )
    }
}
