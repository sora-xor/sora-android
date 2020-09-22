package jp.co.soramitsu.feature_wallet_impl.presentation.receive

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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.util.observeTextChanges
import kotlinx.android.synthetic.main.account_selector_amount.accountAmountBodyTextView
import kotlinx.android.synthetic.main.fragment_receive.qrImg
import kotlinx.android.synthetic.main.fragment_receive.toolbar
import java.io.File
import java.io.FileOutputStream

class ReceiveFragment : BaseFragment<ReceiveViewModel>(), KeyboardHelper.KeyboardListener {

    companion object {
        private const val QR_ANIMATION_DURATION = 200L
        private const val QR_SIZE_CHANGE_COEFFICIENT = 2
        private const val IMAGE_NAME = "image.png"
    }

    private lateinit var btnKeyboard: ImageView

    private var keyboardHelper: KeyboardHelper? = null

    private var maximizedQrSize = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_receive, container, false)

        val accountCurrencySymbol = view.findViewById<TextView>(R.id.currencySymbol)
        accountCurrencySymbol.text = Const.SORA_SYMBOL

        btnKeyboard = view.findViewById(R.id.btn_keyboard)

        return view
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .receiveAmountComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        accountAmountBodyTextView.hint = "0"

        with(toolbar) {
            showShareButton()
            setTitle(getString(R.string.wallet_receive_xor))
            setBackgroundColor(ContextCompat.getColor(activity!!, R.color.backgroundGrey))
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

    override fun subscribe(viewModel: ReceiveViewModel) {
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
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.wallet_share_qr_code))
                        putExtra(Intent.EXTRA_TEXT, it.second)
                    }

                    startActivity(Intent.createChooser(intent, getString(R.string.wallet_share_qr_code)))
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
        keyboardHelper!!.setKeyboardListener(this)
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