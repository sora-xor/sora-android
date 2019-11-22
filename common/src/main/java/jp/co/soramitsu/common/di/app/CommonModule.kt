/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import android.graphics.Color
import android.os.Build
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.AppVersionProviderImpl
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.resourses.ResourceManagerImpl
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.sora.sdk.crypto.json.JSONEd25519Sha3SignatureSuite
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import javax.inject.Singleton

@Module
class CommonModule {

    @Singleton
    @Provides
    fun provideResourceManager(resourceManager: ResourceManagerImpl): ResourceManager = resourceManager

    @Singleton
    @Provides
    fun provideAppVersionProvider(appVersionProvider: AppVersionProviderImpl): AppVersionProvider = appVersionProvider

    @Singleton
    @Provides
    fun providesPushHandler(): PushHandler = PushHandler()

    @Singleton
    @Provides
    fun provideHealthChecker(): HealthChecker = HealthChecker()

    @Singleton
    @Provides
    fun provideQrCodeGenerator(resourceManager: ResourceManager): QrCodeGenerator {
        return QrCodeGenerator(Color.BLACK, resourceManager.getColor(R.color.qrCodeSecondColor))
    }

    @Singleton
    @Provides
    fun provideQrCodeDecoder(context: Context): QrCodeDecoder {
        return QrCodeDecoder(context.contentResolver)
    }

    @Singleton
    @Provides
    fun provideDeviceParams(context: Context): DeviceParamsProvider {
        return DeviceParamsProvider(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
            Build.MODEL,
            Build.VERSION.RELEASE,
            Locale.getDefault().language,
            Locale.getDefault().country,
            TimeZone.getDefault()
        )
    }

    @Singleton
    @Provides
    fun provideMnemonicProvider(): MnemonicProvider = MnemonicProvider()

    @Singleton
    @Provides
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Singleton
    @Provides
    fun provideCryptoAssistant(secureRandom: SecureRandom): CryptoAssistant {
        return CryptoAssistant(secureRandom, JsonUtil.buildMapper(), JSONEd25519Sha3SignatureSuite(), Ed25519Sha3())
    }

    @Provides
    @Singleton
    fun provideDidProvider(): DidProvider {
        return DidProvider(JsonUtil.buildMapper())
    }

    @Provides
    @Singleton
    fun provideInvitationHandler(): InvitationHandler = InvitationHandler()
}