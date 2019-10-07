/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.receiveamount

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.core_ui.presentation.view.CurrencyEditText
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_receive_amount.qrImg
import kotlinx.android.synthetic.main.fragment_receive_amount.toolbar

@SuppressLint("CheckResult")
class ReceiveAmountFragment : BaseFragment<ReceiveAmountViewModel>(), KeyboardHelper.KeyboardListener {

    private lateinit var btnKeyboard: ImageView

    private var keyboardHelper: KeyboardHelper? = null

    private lateinit var accountAmountBodyTextView: CurrencyEditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_receive_amount, container, false)

        accountAmountBodyTextView = view.findViewById(R.id.accountAmountBodyTextView)

        val accountCurrencySymbol = view.findViewById<TextView>(R.id.currencySymbol)
        accountCurrencySymbol.text = Const.SORA_SYMBOL

        btnKeyboard = view.findViewById(R.id.btn_keyboard)

        return view
    }

    override fun initViews() {
        accountAmountBodyTextView.hint = "0"

        toolbar.setTitle(getString(R.string.receive_money))
        toolbar.setBackgroundColor(resources.getColor(R.color.background_color))
        toolbar.setHomeButtonListener {
            if (keyboardHelper?.isKeyboardShowing == true) {
                hideSoftKeyboard(activity)
            } else {
                viewModel.backButtonPressed()
            }
        }
        toolbar.setShareButtonListener {
            viewModel.shareQr(if (accountAmountBodyTextView.text.isNullOrEmpty() || accountAmountBodyTextView.getBigDecimal()!!.toDouble() == 0.0) "" else accountAmountBodyTextView.getBigDecimal().toString())
        }
        toolbar.showShareButton()

        (activity as MainActivity).hideBottomView()
    }

    override fun subscribe(viewModel: ReceiveAmountViewModel) {
        RxView.clicks(btnKeyboard)
            .subscribe {
                if (keyboardHelper!!.isKeyboardShowing)
                    hideSoftKeyboard(activity)
                else
                    openSoftKeyboard(accountAmountBodyTextView)
            }

        RxTextView.textChanges(accountAmountBodyTextView)
            .skipInitialValue()
            .subscribe { generateQr() }

        observe(viewModel.qrBitmapLiveData, Observer {
            qrImg.setImageBitmap(it)
        })

        observe(viewModel.qrCodeAnimatedValue, Observer {
            val layoutParams = qrImg.layoutParams
            layoutParams.height = it
            layoutParams.width = it
            qrImg.layoutParams = layoutParams
        })

        viewModel.generateQr("")
    }

    private fun generateQr() {
        viewModel.generateQr(if (accountAmountBodyTextView.text.isNullOrEmpty()) "" else accountAmountBodyTextView.getBigDecimal().toString())
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .receiveAmountComponentBuilder()
            .withRouter(activity as MainRouter)
            .withFragment(this)
            .build()
            .inject(this)
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
        viewModel.keyBoardHide(qrImg.width)
        btnKeyboard.setImageResource(R.drawable.icon_open_keyboard)
    }

    override fun onKeyboardShow() {
        viewModel.keyboardShown(qrImg.width)
        btnKeyboard.setImageResource(R.drawable.icon_close_keyboard)
    }
}