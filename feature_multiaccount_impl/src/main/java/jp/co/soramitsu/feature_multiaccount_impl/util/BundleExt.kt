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

package jp.co.soramitsu.feature_multiaccount_impl.util

import android.os.Bundle
import jp.co.soramitsu.common.presentation.args.requireString
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel

private const val TYPE_KEY = "TYPE"
var Bundle.type: ExportProtectionViewModel.Type
    get() = ExportProtectionViewModel.Type.valueOf(this.requireString(TYPE_KEY))
    set(value) = this.putString(TYPE_KEY, value.toString())
