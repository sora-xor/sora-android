/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.custom

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.pincode.custom.PinLockAdapter.OnDeleteClickListener
import jp.co.soramitsu.feature_main_impl.presentation.pincode.custom.PinLockAdapter.OnFingerprintClickListener
import jp.co.soramitsu.feature_main_impl.presentation.pincode.custom.PinLockAdapter.OnNumberClickListener

class PinLockView : RecyclerView {

    companion object {

        private const val DEFAULT_PIN_LENGTH = 4
        private val DEFAULT_KEY_SET = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    }

    private var mPin = ""
    private var mPinLength: Int = 0
    private var mHorizontalSpacing: Int = 0
    private var mVerticalSpacing: Int = 0
    private var mTextColor: Int = 0
    private var mDeleteButtonPressedColor: Int = 0
    private var mDeleteButtonColor: Int = 0
    private var mTextSize: Int = 0
    private var mButtonSize: Int = 0
    private var mDeleteButtonSize: Int = 0
    private var mButtonBackgroundDrawable: Drawable? = null
    private var mDeleteButtonDrawable: Drawable? = null
    private var mShowDeleteButton: Boolean = false

    private var mIndicatorDots: IndicatorDots? = null
    private var mAdapter: PinLockAdapter? = null
    private var mPinLockListener: PinLockListener? = null
    private var mCustomizationOptionsBundle: CustomizationOptionsBundle? = null
    private var mCustomKeySet: IntArray? = null

    private val mOnNumberClickListener = object : OnNumberClickListener {
        override fun onNumberClicked(keyValue: Int) {
            if (mPin.length < pinLength) {
                mPin += keyValue.toString()

                if (isIndicatorDotsAttached) {
                    mIndicatorDots!!.updateDot(mPin.length)
                }

                if (mPin.length == 1) {
                    mAdapter!!.pinLength = mPin.length
                    mAdapter!!.notifyItemChanged(mAdapter!!.itemCount - 1)
                }

                if (mPinLockListener != null) {
                    if (mPin.length == mPinLength) {
                        mPinLockListener!!.onComplete(mPin)
                    } else {
                        mPinLockListener!!.onPinChange(mPin.length, mPin)
                    }
                }
            } else {
                if (!isShowDeleteButton) {
                    resetPinLockView()
                    mPin = mPin + keyValue.toString()

                    if (isIndicatorDotsAttached) {
                        mIndicatorDots!!.updateDot(mPin.length)
                    }

                    if (mPinLockListener != null) {
                        mPinLockListener!!.onPinChange(mPin.length, mPin)
                    }
                } else {
                    if (mPinLockListener != null) {
                        mPinLockListener!!.onComplete(mPin)
                    }
                }
            }
        }
    }

    private val mOnDeleteClickListener = object : OnDeleteClickListener {
        override fun onDeleteClicked() {
            if (mPin.isNotEmpty()) {
                mPin = mPin.substring(0, mPin.length - 1)

                if (isIndicatorDotsAttached) {
                    mIndicatorDots!!.updateDot(mPin.length)
                }

                if (mPin.isEmpty()) {
                    mAdapter!!.pinLength = mPin.length
                    mAdapter!!.notifyItemChanged(mAdapter!!.itemCount - 1)
                }

                if (mPinLockListener != null) {
                    if (mPin.isEmpty()) {
                        mPinLockListener!!.onEmpty()
                        mPin = ""
                    } else {
                        mPinLockListener!!.onPinChange(mPin.length, mPin)
                    }
                }
            } else {
                if (mPinLockListener != null) {
                    mPinLockListener!!.onEmpty()
                }
            }
        }

        override fun onDeleteLongClicked() {
            resetPinLockView()
            if (mPinLockListener != null) {
                mPinLockListener!!.onEmpty()
            }
        }
    }

    private val mOnFingerprintClickListener = object : OnFingerprintClickListener {
        override fun onFingerprintClicked() {
            if (mPinLockListener != null) {
                mPinLockListener!!.onFingerprintButtonClicked()
            }
        }
    }

    var pinLength: Int
        get() = mPinLength
        set(pinLength) {
            this.mPinLength = pinLength

            if (isIndicatorDotsAttached) {
                mIndicatorDots!!.pinLength = pinLength
            }
        }

    var textColor: Int
        get() = mTextColor
        set(textColor) {
            this.mTextColor = textColor
            mCustomizationOptionsBundle!!.textColor = textColor
            mAdapter!!.notifyDataSetChanged()
        }

    var textSize: Int
        get() = mTextSize
        set(textSize) {
            this.mTextSize = textSize
            mCustomizationOptionsBundle!!.textSize = textSize
            mAdapter!!.notifyDataSetChanged()
        }

    var buttonSize: Int
        get() = mButtonSize
        set(buttonSize) {
            this.mButtonSize = buttonSize
            mCustomizationOptionsBundle!!.buttonSize = buttonSize
            mAdapter!!.notifyDataSetChanged()
        }

    var buttonBackgroundDrawable: Drawable?
        get() = mButtonBackgroundDrawable
        set(buttonBackgroundDrawable) {
            this.mButtonBackgroundDrawable = buttonBackgroundDrawable
            mCustomizationOptionsBundle!!.buttonBackgroundDrawable = buttonBackgroundDrawable
            mAdapter!!.notifyDataSetChanged()
        }

    var deleteButtonDrawable: Drawable?
        get() = mDeleteButtonDrawable
        set(deleteBackgroundDrawable) {
            this.mDeleteButtonDrawable = deleteBackgroundDrawable
            mCustomizationOptionsBundle!!.deleteButtonDrawable = deleteBackgroundDrawable
            mAdapter!!.notifyDataSetChanged()
        }

    var deleteButtonSize: Int
        get() = mDeleteButtonSize
        set(deleteButtonSize) {
            this.mDeleteButtonSize = deleteButtonSize
            mCustomizationOptionsBundle!!.deleteButtonSize = deleteButtonSize
            mAdapter!!.notifyDataSetChanged()
        }

    var isShowDeleteButton: Boolean
        get() = mShowDeleteButton
        set(showDeleteButton) {
            this.mShowDeleteButton = showDeleteButton
            mCustomizationOptionsBundle!!.isShowDeleteButton = showDeleteButton
            mAdapter!!.notifyDataSetChanged()
        }

    var deleteButtonPressedColor: Int
        get() = mDeleteButtonPressedColor
        set(deleteButtonPressedColor) {
            this.mDeleteButtonPressedColor = deleteButtonPressedColor
            mCustomizationOptionsBundle!!.deleteButtonPressesColor = deleteButtonPressedColor
            mAdapter!!.notifyDataSetChanged()
        }

    var customKeySet: IntArray?
        get() = mCustomKeySet
        set(customKeySet) {
            this.mCustomKeySet = customKeySet
            if (mAdapter != null) {
                mAdapter!!.setKeyValues(customKeySet!!)
            }
        }

    val isIndicatorDotsAttached: Boolean
        get() = mIndicatorDots != null

    var isFingerprintButtonNeeded: Boolean
        get() = mAdapter!!.getIsFingerprintButtonNeeded()
        set(isFingerprintButtonNeeded) {
            mAdapter!!.setIsFingerprintButtonNeeded(isFingerprintButtonNeeded)
            mAdapter!!.notifyDataSetChanged()
        }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attributeSet: AttributeSet?, defStyle: Int) {

        val typedArray = context
            .obtainStyledAttributes(attributeSet, R.styleable.PinLockView)

        try {
            mPinLength = typedArray.getInt(R.styleable.PinLockView_pinLength, DEFAULT_PIN_LENGTH)
            mHorizontalSpacing = typedArray
                .getDimension(R.styleable.PinLockView_keypadHorizontalSpacing,
                    ResourceUtils.getDimensionInPx(context, R.dimen.default_horizontal_spacing)).toInt()
            mVerticalSpacing = typedArray
                .getDimension(R.styleable.PinLockView_keypadVerticalSpacing,
                    ResourceUtils.getDimensionInPx(context, R.dimen.default_vertical_spacing)).toInt()
            mTextColor = typedArray.getColor(R.styleable.PinLockView_keypadTextColor,
                ResourceUtils.getColor(context, android.R.color.white))
            mTextSize = typedArray.getDimension(R.styleable.PinLockView_keypadTextSize,
                ResourceUtils.getDimensionInPx(context, R.dimen.title_text_size)).toInt()
            mButtonSize = typedArray.getDimension(R.styleable.PinLockView_keypadButtonSize,
                ResourceUtils.getDimensionInPx(context, R.dimen.default_button_size)).toInt()
            mDeleteButtonSize = typedArray
                .getDimension(R.styleable.PinLockView_keypadDeleteButtonSize,
                    ResourceUtils.getDimensionInPx(context, R.dimen.default_delete_button_size)).toInt()
            mButtonBackgroundDrawable = context.resources
                .getDrawable(R.drawable.circle_pincode_drawable_default)
            mDeleteButtonDrawable = typedArray
                .getDrawable(R.styleable.PinLockView_keypadDeleteButtonDrawable)
            mShowDeleteButton = typedArray
                .getBoolean(R.styleable.PinLockView_keypadShowDeleteButton, true)
            mDeleteButtonPressedColor = typedArray
                .getColor(R.styleable.PinLockView_keypadDeleteButtonPressedColor,
                    ResourceUtils.getColor(context, R.color.cardview_dark_background))
            mDeleteButtonColor = typedArray
                .getColor(R.styleable.PinLockView_keypadDeleteButtonColor,
                    ResourceUtils.getColor(context, R.color.cardview_dark_background))
        } finally {
            typedArray.recycle()
        }

        mCustomizationOptionsBundle = CustomizationOptionsBundle()
        mCustomizationOptionsBundle!!.textColor = mTextColor
        mCustomizationOptionsBundle!!.textSize = mTextSize
        mCustomizationOptionsBundle!!.buttonSize = mButtonSize
        mCustomizationOptionsBundle!!.buttonBackgroundDrawable = mButtonBackgroundDrawable
        mCustomizationOptionsBundle!!.deleteButtonDrawable = mDeleteButtonDrawable
        mCustomizationOptionsBundle!!.deleteButtonSize = mDeleteButtonSize
        mCustomizationOptionsBundle!!.isShowDeleteButton = mShowDeleteButton
        mCustomizationOptionsBundle!!.deleteButtonColor = mDeleteButtonColor
        mCustomizationOptionsBundle!!.deleteButtonPressesColor = mDeleteButtonPressedColor

        initView()
    }

    private fun initView() {
        layoutManager = LTRGridLayoutManager(context, 3)

        mAdapter = PinLockAdapter(context)
        mAdapter!!.onItemClickListener = mOnNumberClickListener
        mAdapter!!.onDeleteClickListener = mOnDeleteClickListener
        mAdapter!!.setOnFingerprintClickListener(mOnFingerprintClickListener)
        mAdapter!!.customizationOptions = mCustomizationOptionsBundle
        adapter = mAdapter

        addItemDecoration(ItemSpaceDecoration(mHorizontalSpacing, mVerticalSpacing, 3, false))
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    fun setPinLockListener(pinLockListener: PinLockListener) {
        this.mPinLockListener = pinLockListener
    }

    fun resetPinLockView() {
        mPin = ""

        mAdapter!!.pinLength = mPin.length
        mAdapter!!.notifyItemChanged(mAdapter!!.itemCount - 1)

        if (mIndicatorDots != null) {
            mIndicatorDots!!.updateDot(mPin.length)
        }
    }

    fun attachIndicatorDots(mIndicatorDots: IndicatorDots) {
        this.mIndicatorDots = mIndicatorDots
    }
}
