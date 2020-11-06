package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.cardview.widget.CardView
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_asset.view.assetFirstNameTv
import kotlinx.android.synthetic.main.view_asset.view.assetIconView
import kotlinx.android.synthetic.main.view_asset.view.assetLastNameTv
import kotlinx.android.synthetic.main.view_asset.view.associatingStateView
import kotlinx.android.synthetic.main.view_asset.view.balanceTv
import kotlinx.android.synthetic.main.view_asset.view.errorStateView
import kotlinx.android.synthetic.main.view_asset.view.iconImg
import kotlinx.android.synthetic.main.view_asset.view.normalStateView

class AssetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    enum class State {
        NORMAL,
        ASSOCIATING,
        ERROR
    }

    init {
        View.inflate(context, R.layout.view_asset, this)
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AssetView)

            val assetIcon = typedArray.getDrawable(R.styleable.AssetView_assetIcon)
            assetIcon?.let { setAssetIconDrawable(it) }

            val assetBalanceText = typedArray.getString(R.styleable.AssetView_assetBalanceText)
            assetBalanceText?.let { setBalance(it) }

            val assetNameText = typedArray.getString(R.styleable.AssetView_assetName)
            assetNameText?.let { setAssetFirstName(it) }

            val assetBlockChainNameText = typedArray.getString(R.styleable.AssetView_assetBlockChainName)
            assetBlockChainNameText?.let { setAssetLastName(it) }

            val assetIconViewBackgroundColor = typedArray.getColor(R.styleable.AssetView_assetIconViewBackgroundColor, 0)
            if (assetIconViewBackgroundColor != 0) {
                setAssetIconViewBackgroundColor(assetIconViewBackgroundColor)
            }

            typedArray.recycle()
        }
    }

    fun setBalance(balance: String) {
        balanceTv.text = balance
    }

    fun setAssetFirstName(assetName: String) {
        assetFirstNameTv.text = assetName
    }

    fun setAssetLastName(blockChainName: String) {
        assetLastNameTv.text = blockChainName
    }

    fun setAssetIconDrawable(assetIconDrawable: Drawable) {
        iconImg.setImageDrawable(assetIconDrawable)
    }

    fun setAssetIconResource(assetIconRes: Int) {
        iconImg.setImageResource(assetIconRes)
    }

    fun setAssetIconViewBackgroundColor(color: Int) {
        assetIconView.setBackgroundColor(color)
    }

    fun changeState(state: State) {
        when (state) {
            State.NORMAL -> showNormalState()
            State.ASSOCIATING -> showAssociatingState()
            State.ERROR -> showErrorState()
        }
    }

    private fun showNormalState() {
        normalStateView.visibility = View.VISIBLE
        associatingStateView.visibility = View.GONE
        errorStateView.visibility = View.GONE
    }

    private fun showAssociatingState() {
        normalStateView.visibility = View.GONE
        associatingStateView.visibility = View.VISIBLE
        errorStateView.visibility = View.GONE
    }

    private fun showErrorState() {
        normalStateView.visibility = View.GONE
        associatingStateView.visibility = View.GONE
        errorStateView.visibility = View.VISIBLE
    }
}