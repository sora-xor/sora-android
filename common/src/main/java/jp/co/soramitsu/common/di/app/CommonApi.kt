/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.common.util.QrCodeGenerator

interface CommonApi {

    fun context(): Context

    fun prefsUtil(): PrefsUtil

    fun resourceManager(): ResourceManager

    fun appVersionProvider(): AppVersionProvider

    fun numbersFormatter(): NumbersFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker

    fun qrCodeGenerator(): QrCodeGenerator

    fun qrCodeDecoder(): QrCodeDecoder

    fun deviceParams(): DeviceParamsProvider

    fun mnemonicProvider(): MnemonicProvider

    fun cryptoAssistsant(): CryptoAssistant

    fun didProvider(): DidProvider

    fun invitationHandler(): InvitationHandler
}