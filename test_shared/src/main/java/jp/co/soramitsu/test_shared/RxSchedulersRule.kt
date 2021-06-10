/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_shared

import io.reactivex.Scheduler
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.Callable

class RxSchedulersRule : TestRule {

    private val SCHEDULER_INSTANCE = Schedulers.trampoline()

    private val schedulerMapper = { _: Scheduler -> SCHEDULER_INSTANCE }
    private val schedulerMapperLazy = { _: Callable<Scheduler> -> SCHEDULER_INSTANCE }

    override fun apply(base: Statement, description: Description): Statement {

        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                RxAndroidPlugins.reset()
                RxAndroidPlugins.setInitMainThreadSchedulerHandler(schedulerMapperLazy)

                RxJavaPlugins.reset()
                RxJavaPlugins.setIoSchedulerHandler(schedulerMapper)
                RxJavaPlugins.setNewThreadSchedulerHandler(schedulerMapper)
                RxJavaPlugins.setComputationSchedulerHandler(schedulerMapper)

                base.evaluate()

                RxAndroidPlugins.reset()
                RxJavaPlugins.reset()
            }
        }
    }
}
