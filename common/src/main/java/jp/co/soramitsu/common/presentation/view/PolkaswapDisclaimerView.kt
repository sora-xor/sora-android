/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
