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

package jp.co.soramitsu.feature_main_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

class PinCodeInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val walletRepository: WalletRepository
) {

    companion object {
        const val PINCODE_LENGTH = 6
        const val OLD_PINCODE_LENGTH = 4
    }

    suspend fun savePin(pin: String) {
        return userRepository.savePin(pin)
    }

    suspend fun checkPin(code: String) = userRepository.retrievePin() == code

    suspend fun isCodeSet(): Boolean {
        return userRepository.retrievePin().isNotEmpty()
    }

    suspend fun fullLogout() {
        userRepository.fullLogout()
    }

    suspend fun clearAccountData(address: String) {
        userRepository.clearAccountData(address)
    }

    suspend fun setBiometryAvailable(isBiometryAvailable: Boolean) {
        userRepository.setBiometryAvailable(isBiometryAvailable)
    }

    suspend fun isBiometryAvailable(): Boolean {
        return userRepository.isBiometryAvailable()
    }

    suspend fun isBiometryEnabled(): Boolean {
        return userRepository.isBiometryEnabled()
    }

    suspend fun needsMigration(): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val isFetched = userRepository.isMigrationFetched(soraAccount)
        return if (isFetched) {
            userRepository.needsMigration(soraAccount)
        } else {
            val irohaData = credentialsRepository.getIrohaData(soraAccount)
            val needs = walletRepository.needsMigration(irohaData.address)
            userRepository.saveNeedsMigration(needs, soraAccount)
            userRepository.saveIsMigrationFetched(true, soraAccount)
            userRepository.needsMigration(soraAccount)
        }
    }

    suspend fun setBiometryEnabled(isEnabled: Boolean) {
        userRepository.setBiometryEnabled(isEnabled)
    }

    suspend fun isPincodeUpdateNeeded(): Boolean {
        val pinLength = userRepository.retrievePin().length
        return pinLength > 0 && pinLength != PINCODE_LENGTH
    }

    suspend fun saveTriesUsed(triesUsed: Int) {
        userRepository.savePinTriesUsed(triesUsed)
    }

    suspend fun retrieveTriesUsed() = userRepository.retrievePinTriesUsed()

    suspend fun retrieveTimerStartedTimestamp() = userRepository.retrieveTimerStartedTimestamp()

    suspend fun resetTriesUsed() {
        userRepository.resetTriesUsed()
    }

    suspend fun resetTimerStartedTimestamp() {
        userRepository.resetTimerStartedTimestamp()
    }

    suspend fun saveTimerStartedTimestamp(timestamp: Long) {
        userRepository.saveTimerStartedTimestamp(timestamp)
    }
}
