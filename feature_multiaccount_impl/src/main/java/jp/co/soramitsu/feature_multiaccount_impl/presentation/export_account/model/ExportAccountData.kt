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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.account.SoraAccount

data class ExportAccountData(
    val isSelected: Boolean,
    val isSelectedAction: Boolean,
    val icon: Drawable,
    val account: SoraAccount,
    /**
     * The account is listed but cannot sign on this phone: its row says so. It can still be
     * selected (balances and history show) and repaired from its options.
     */
    val readOnly: Boolean = false,
)
