package jp.co.soramitsu.common.logger

import com.orhanobut.logger.DiskLogAdapter
import jp.co.soramitsu.common.BuildConfig

class DiskLoggerAdapter : DiskLogAdapter() {

    override fun isLoggable(priority: Int, tag: String?): Boolean {
        return BuildConfig.BUILD_TYPE == "debug"
    }
}