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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model

import jp.co.soramitsu.common.util.StringTriple

data class EditFarmScreenState(
    val farmIds: StringTriple,
    val percentageText: String,
    val sliderProgressState: Float,
    val poolShareStaked: String,
    val poolShareStakedWillBe: String,
    val fee: String,
    val networkFee: String,
    val isButtonLoading: Boolean = false,
    val isButtonActive: Boolean = false,
    val isCardLoading: Boolean = true
)
