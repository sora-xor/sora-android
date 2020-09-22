package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.auth.AuthHolder
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_ethereum_api.EthServiceStarter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository

interface WalletFeatureDependencies {

    fun preferences(): Preferences

    fun authHolder(): AuthHolder

    fun networkApiCreator(): NetworkApiCreator

    fun serializer(): Serializer

    fun appDatabase(): AppDatabase

    fun didRepository(): DidRepository

    fun resourceManager(): ResourceManager

    fun dateTimeFormatter(): DateTimeFormatter

    fun qrCodeGenerator(): QrCodeGenerator

    fun debounceClickHandler(): DebounceClickHandler

    fun pushHandler(): PushHandler

    fun numbersFormatter(): NumbersFormatter

    fun textFormatter(): TextFormatter

    fun clipboardManager(): ClipboardManager

    fun appLinksProvider(): AppLinksProvider

    fun context(): Context

    fun ethereumInteractor(): EthereumInteractor

    fun ethereumRepository(): EthereumRepository

    fun assetHolder(): AssetHolder

    fun ethServiceStarter(): EthServiceStarter
}