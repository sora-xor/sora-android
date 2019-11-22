/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.receiveamount

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.observeTextChanges
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.core_ui.presentation.view.CurrencyEditText
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_receive_amount.qrImg
import kotlinx.android.synthetic.main.fragment_receive_amount.toolbar
import java.io.File
import java.io.FileOutputStream

class ReceiveAmountFragment : BaseFragment<ReceiveAmountViewModel>(), KeyboardHelper.KeyboardListener {

    companion object {
        private const val QR_ANIMATION_DURATION = 200L
        private const val QR_SIZE_CHANGE_COEFFICIENT = 2
        private const val IMAGE_NAME = "image.png"
    }

    private lateinit var btnKeyboard: ImageView

    private var keyboardHelper: KeyboardHelper? = null

    private lateinit var accountAmountBodyTextView: CurrencyEditText

    private var maximizedQrSize = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_receive_amount, container, false)

        accountAmountBodyTextView = view.findViewById(R.id.accountAmountBodyTextView)

        val accountCurrencySymbol = view.findViewById<TextView>(R.id.currencySymbol)
        accountCurrencySymbol.text = Const.SORA_SYMBOL

        btnKeyboard = view.findViewById(R.id.btn_keyboard)

        return view
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .receiveAmountComponentBuilder()
            .withRouter(activity as MainRouter)
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as MainActivity).hideBottomView()
        accountAmountBodyTextView.hint = "0"

        with(toolbar) {
            showShareButton()
            setTitle(getString(R.string.receive_xor))
            setBackgroundColor(resources.getColor(R.color.background_color))
            setHomeButtonListener {
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard(activity)
                } else {
                    viewModel.backButtonPressed()
                }
            }
            setShareButtonListener {
                viewModel.shareQr()
            }
        }
    }

    override fun subscribe(viewModel: ReceiveAmountViewModel) {
        btnKeyboard.setOnClickListener {
            if (keyboardHelper!!.isKeyboardShowing) {
                hideSoftKeyboard(activity)
            } else {
                openSoftKeyboard(accountAmountBodyTextView)
            }
        }

        observe(viewModel.qrBitmapLiveData, Observer {
            qrImg.setImageBitmap(it)
        })

        observe(viewModel.shareQrCodeLiveData, EventObserver {
            val mediaStorageDir: File? = saveToTempFile(activity!!, it.first)

            if (mediaStorageDir != null) {
                val imageUri = FileProvider.getUriForFile(activity!!, "${activity!!.packageName}.provider", mediaStorageDir)

                if (imageUri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_title))
                        putExtra(Intent.EXTRA_TEXT, it.second)
                    }

                    startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
                }
            }
        })

        viewModel.subscribeOnTextChanges(accountAmountBodyTextView.observeTextChanges())
    }

    private fun saveToTempFile(context: Context, bitmap: Bitmap): File? {
        val mediaStorageDir = File(context.externalCacheDir!!.absolutePath + IMAGE_NAME)

        val outputStream = FileOutputStream(mediaStorageDir)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()
        return mediaStorageDir
    }

    override fun onResume() {
        super.onResume()
        activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        keyboardHelper = KeyboardHelper(view!!)
        keyboardHelper!!.setKeyboardListener(this@ReceiveAmountFragment)
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
    }

    override fun onKeyboardHide() {
        if (maximizedQrSize != qrImg.width) {
            resizeQrImageView(qrImg.width, qrImg.width * QR_SIZE_CHANGE_COEFFICIENT)
        }
        btnKeyboard.setImageResource(R.drawable.icon_open_keyboard)
    }

    override fun onKeyboardShow() {
        if (maximizedQrSize == 0) maximizedQrSize = qrImg.width

        if (maximizedQrSize == qrImg.width) {
            resizeQrImageView(qrImg.width, qrImg.width / QR_SIZE_CHANGE_COEFFICIENT)
        }
        btnKeyboard.setImageResource(R.drawable.icon_close_keyboard)
    }

    private fun resizeQrImageView(width: Int, finalWidth: Int) {
        val anim = ValueAnimator.ofInt(width, finalWidth)
        anim.addUpdateListener {
            if (isAdded) {
                val animatedValue = it.animatedValue as Int
                val layoutParams = qrImg.layoutParams
                layoutParams.height = animatedValue
                layoutParams.width = animatedValue
                qrImg.layoutParams = layoutParams
            }
        }
        anim.duration = QR_ANIMATION_DURATION
        anim.start()
    }
}