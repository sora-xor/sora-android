package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.AssetCardViewBinding
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.util.ext.show

class AssetCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = AssetCardViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        this.background = ContextCompat.getDrawable(context, R.drawable.background_rounded_grey_100)
        val paddingHorizontalPixels = context.resources.getDimension(R.dimen.x1).toInt()
        val paddingVerticalPixels = context.resources.getDimension(R.dimen.x1_2).toInt()
        setPaddingRelative(
            paddingHorizontalPixels,
            paddingVerticalPixels,
            paddingHorizontalPixels,
            paddingVerticalPixels
        )
    }

    fun setAsset(assetListItemModel: Asset) {
        binding.text.text = assetListItemModel.token.symbol
        binding.icon.setImageResource(assetListItemModel.token.icon)
        binding.icon.show()
    }

    fun resetChevron() {
        binding.chevronIcon.setImageResource(R.drawable.ic_chevron_down_rounded_16)
    }

    fun setClickListener(listener: () -> Unit) {
        setOnClickListener {
            binding.chevronIcon.setImageResource(R.drawable.ic_chevron_up_rounded_16)
            listener()
        }
    }
}
