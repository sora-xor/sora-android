/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.custom

import android.content.Context
import android.graphics.PorterDuff.Mode
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_main_impl.R

class PinLockAdapter(
    private val mContext: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_NUMBER = 0
        private const val VIEW_TYPE_DELETE = 1
        private const val VIEW_TYPE_FINGERPRINT = 2
    }

    var customizationOptions: CustomizationOptionsBundle? = null
    var onItemClickListener: OnNumberClickListener? = null
    var onDeleteClickListener: OnDeleteClickListener? = null
    var pinLength: Int = 0

    private var mKeyValues: IntArray? = null
    private var mOnFingerprintClickListener: OnFingerprintClickListener? = null
    private var isFingerprintButtonNeeded = false

    interface OnNumberClickListener {

        fun onNumberClicked(keyValue: Int)
    }

    interface OnDeleteClickListener {

        fun onDeleteClicked()

        fun onDeleteLongClicked()
    }

    interface OnFingerprintClickListener {

        fun onFingerprintClicked()
    }

    init {
        this.mKeyValues = getAdjustKeyValues(intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0))
    }

    fun getKeyValues(): IntArray? {
        return mKeyValues
    }

    fun setKeyValues(keyValues: IntArray) {
        this.mKeyValues = getAdjustKeyValues(keyValues)
        notifyDataSetChanged()
    }

    fun getIsFingerprintButtonNeeded(): Boolean {
        return isFingerprintButtonNeeded
    }

    fun setIsFingerprintButtonNeeded(isFingerprintButtonNeeded: Boolean) {
        this.isFingerprintButtonNeeded = isFingerprintButtonNeeded
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: View

        return when (viewType) {
            VIEW_TYPE_NUMBER -> {
                view = inflater.inflate(R.layout.layout_number_item, parent, false)
                NumberViewHolder(view)
            }
            VIEW_TYPE_DELETE -> {
                view = inflater.inflate(R.layout.layout_delete_item, parent, false)
                DeleteViewHolder(view)
            }
            else -> {
                view = inflater.inflate(R.layout.layout_fingerprint_item, parent, false)
                FingerprintViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_NUMBER -> {
                val vh1 = holder as NumberViewHolder
                configureNumberButtonHolder(vh1, position)
            }
            VIEW_TYPE_DELETE -> {
                val vh2 = holder as DeleteViewHolder
                configureDeleteButtonHolder(vh2)
            }
            VIEW_TYPE_FINGERPRINT -> {
                val fingerprintViewHolder = holder as FingerprintViewHolder
                configureFingerprintButtonHolder(fingerprintViewHolder)
            }
        }
    }

    private fun configureNumberButtonHolder(holder: NumberViewHolder?, position: Int) {
        if (holder != null) {
            if (position == 9) {
                holder.mNumberButton.visibility = View.GONE
            } else {
                holder.mNumberButton.text = mKeyValues!![position].toString()
                holder.mNumberButton.visibility = View.VISIBLE
                holder.mNumberButton.tag = mKeyValues!![position]
            }

            if (customizationOptions != null) {
                holder.mNumberButton.setTextColor(customizationOptions!!.textColor)
                if (customizationOptions!!.buttonBackgroundDrawable != null) {

                    holder.mNumberButton.setBackgroundResource(R.drawable.circle_pincode_drawable_default)
                }
                holder.mNumberButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    customizationOptions!!.textSize.toFloat())
                val params = LinearLayout.LayoutParams(
                    customizationOptions!!.buttonSize,
                    customizationOptions!!.buttonSize)
                val px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    mContext.resources.getDimension(R.dimen.x1_2),
                    mContext.resources.displayMetrics
                ).toInt()

                params.setMargins(px, px, px, px)
                holder.mNumberButton.layoutParams = params
            }
        }
    }

    private fun configureDeleteButtonHolder(holder: DeleteViewHolder) {
        if (customizationOptions!!.isShowDeleteButton && pinLength > 0) {
            holder.mDeleteButton.visibility = View.VISIBLE
            if (customizationOptions!!.deleteButtonDrawable != null) {
                holder.mDeleteButton.setImageDrawable(customizationOptions!!.deleteButtonDrawable)
            }
            holder.mDeleteButton.drawable.setColorFilter(customizationOptions!!.deleteButtonColor, Mode.SRC_ATOP)
            val params = LinearLayout.LayoutParams(customizationOptions!!.buttonSize, customizationOptions!!.buttonSize)
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mContext.resources.getDimension(R.dimen.x1_2),
                mContext.resources.displayMetrics
            ).toInt()

            params.setMargins(px, px, px, px)
            holder.mDeleteButton.layoutParams = params
        } else {
            holder.mDeleteButton.visibility = View.GONE
        }
    }

    private fun configureFingerprintButtonHolder(holder: FingerprintViewHolder?) {
        if (holder != null) {
            holder.mFingerprintButton.drawable.setColorFilter(customizationOptions!!.deleteButtonColor,
                Mode.SRC_ATOP)
            val params = LinearLayout.LayoutParams(
                customizationOptions!!.buttonSize,
                customizationOptions!!.buttonSize)
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mContext.resources.getDimension(R.dimen.x1_2),
                mContext.resources.displayMetrics
            ).toInt()

            params.setMargins(px, px, px, px)
            holder.mFingerprintButton.layoutParams = params

            holder.mFingerprintButton.visibility = if (isFingerprintButtonNeeded) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount(): Int {
        return 12
    }

    override fun getItemViewType(position: Int): Int {
        if (position == itemCount - 1)
            return VIEW_TYPE_DELETE

        return if (position == itemCount - 3) VIEW_TYPE_FINGERPRINT else VIEW_TYPE_NUMBER
    }

    private fun getAdjustKeyValues(keyValues: IntArray): IntArray {
        val adjustedKeyValues = IntArray(keyValues.size + 1)
        for (i in keyValues.indices) {
            if (i < 9) {
                adjustedKeyValues[i] = keyValues[i]
            } else {
                adjustedKeyValues[i] = -1
                adjustedKeyValues[i + 1] = keyValues[i]
            }
        }
        return adjustedKeyValues
    }

    fun setOnFingerprintClickListener(onFingerprintClickListener: OnFingerprintClickListener) {
        this.mOnFingerprintClickListener = onFingerprintClickListener
    }

    inner class NumberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal var mNumberButton: Button = itemView.findViewById(R.id.button)

        init {
            mNumberButton.setOnClickListener { v ->
                if (onItemClickListener != null) {
                    onItemClickListener!!.onNumberClicked(v.tag as Int)
                }
            }
        }
    }

    inner class DeleteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal var mDeleteButton: AppCompatImageButton = itemView.findViewById(R.id.pin_delete_button)

        init {
            mDeleteButton.drawable.setColorFilter(customizationOptions!!.deleteButtonColor, Mode.OVERLAY)
            mDeleteButton.scaleType = ScaleType.CENTER_INSIDE

            if (customizationOptions!!.isShowDeleteButton) {
                mDeleteButton.setOnClickListener { v ->
                    if (onDeleteClickListener != null) {
                        onDeleteClickListener!!.onDeleteClicked()
                    }
                }

                mDeleteButton.setOnLongClickListener { v ->
                    if (onDeleteClickListener != null) {
                        onDeleteClickListener!!.onDeleteLongClicked()
                    }
                    true
                }
            }
        }
    }

    inner class FingerprintViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal var mFingerprintButton: AppCompatImageButton = itemView.findViewById(R.id.pin_fingerprint_button)

        init {
            mFingerprintButton.drawable.setColorFilter(customizationOptions!!.deleteButtonColor, Mode.OVERLAY)
            mFingerprintButton.scaleType = ScaleType.CENTER_INSIDE
            mFingerprintButton.setOnClickListener { v -> mOnFingerprintClickListener!!.onFingerprintClicked() }
        }
    }
}