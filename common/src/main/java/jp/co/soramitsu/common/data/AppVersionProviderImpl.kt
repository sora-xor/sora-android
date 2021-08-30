package jp.co.soramitsu.common.data

import android.content.Context
import jp.co.soramitsu.common.domain.AppVersionProvider
import javax.inject.Inject

class AppVersionProviderImpl @Inject constructor(
    context: Context
) : AppVersionProvider {

    private val mVersionName: String

    init {
        val packageInfo = context.applicationContext.packageManager.getPackageInfo(context.packageName, 0)
        mVersionName = packageInfo.versionName
    }

    override fun getVersionName() = mVersionName
}
