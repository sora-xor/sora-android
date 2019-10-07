/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.interfaces

import androidx.lifecycle.LiveData
import io.reactivex.CompletableTransformer
import io.reactivex.SingleTransformer

interface WithPreloader {

    fun getPreloadVisibility(): LiveData<Boolean>

    fun <T> preloadCompose(): SingleTransformer<T, T>

    fun preloadCompletableCompose(): CompletableTransformer

    fun showPreloader()

    fun hidePreloader()
}