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

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.PolkaswapDisclaimerViewBinding
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.highlightWords

class PolkaswapDisclaimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = PolkaswapDisclaimerViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        val color = getContext().attrColor(R.attr.polkaswapPrimary)
        val text = getContext().getString(R.string.polkaswap_info_text_1).highlightWords(
            listOf(
                color,
                color,
                color,
            ),
            listOf(
                { ShareUtil.shareInBrowser(getContext(), Const.POLKASWAP_FAQ) },
                { ShareUtil.shareInBrowser(getContext(), Const.POLKASWAP_MEMORANDUM) },
                { ShareUtil.shareInBrowser(getContext(), Const.POLKASWAP_PRIVACY_POLICY) }
            ),
            true,
        )

        binding.tvPolkaswapText1.setText(
            text,
            TextView.BufferType.SPANNABLE
        )
        binding.tvPolkaswapText1.movementMethod = LinkMovementMethod.getInstance()

        getContext().getString(R.string.polkaswap_info_text_6).highlightWords(
            listOf(
                color,
                color,
                color,
            ),
            listOf(
                { ShareUtil.shareInBrowser(getContext(), Const.POLKASWAP_FAQ) },
                { ShareUtil.shareInBrowser(getContext(), Const.POLKASWAP_MEMORANDUM) },
                { ShareUtil.shareInBrowser(getContext(), Const.POLKASWAP_PRIVACY_POLICY) }
            ),
            true,
        ).also {
            binding.tvPolkaswapText6.setText(
                it,
                TextView.BufferType.SPANNABLE
            )
            binding.tvPolkaswapText6.movementMethod = LinkMovementMethod.getInstance()
        }

        binding.tvPolkaswapText1.highlightColor = Color.TRANSPARENT
        binding.tvPolkaswapText1.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPolkaswapText6.highlightColor = Color.TRANSPARENT
        binding.tvPolkaswapText6.movementMethod = LinkMovementMethod.getInstance()
    }
}
