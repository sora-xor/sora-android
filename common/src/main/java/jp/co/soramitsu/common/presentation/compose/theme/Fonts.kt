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

package jp.co.soramitsu.common.presentation.compose.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import jp.co.soramitsu.common.R

internal val Sora = FontFamily(
    Font(R.font.sora_extrabold, FontWeight.ExtraBold),
    Font(R.font.sora_bold, FontWeight.Bold),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_light, FontWeight.Light),
    Font(R.font.sora_extralight, FontWeight.ExtraLight),
    Font(R.font.sora_thin, FontWeight.Thin),
)

internal val Inter = FontFamily(
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_thin, FontWeight.Thin),
)
