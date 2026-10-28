/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.delegate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.interfaces.WithProgress

class WithProgressImpl : WithProgress {

    private val progressVisibilityLiveData = MutableLiveData<Boolean>()

    override fun getProgressVisibility(): LiveData<Boolean> {
        return progressVisibilityLiveData
    }

    override fun showProgress() {
        progressVisibilityLiveData.postValue(true)
    }

    override fun hideProgress() {
        progressVisibilityLiveData.postValue(false)
    }
}
