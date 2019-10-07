/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.receiveamount

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class ReceiveAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    companion object {
        private const val QR_ANIMATION_DURATION = 200L
        private const val QR_SIZE_CHANGE_COEFFICIENT = 2
        private const val RECEIVE_QR_SCALE_SIZE = 1024
        private const val PADDING_SIZE = 2
        private val QR_SECOND_COLOR = Color.parseColor("#f2f2f2")
    }

    val qrCodeAnimatedValue = MutableLiveData<Int>()
    val qrBitmapLiveData = MutableLiveData<Bitmap>()

    private var maximizedQrSize = 0

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun generateQr(amount: String) {
        disposables.add(
            interactor.getQrCodeAmountString(amount)
                .map { generateQrBitmap(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    qrBitmapLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun keyboardShown(qrCurrentWidth: Int) {
        if (maximizedQrSize == 0) maximizedQrSize = qrCurrentWidth

        if (maximizedQrSize == qrCurrentWidth) {
            resizeQrImageView(qrCurrentWidth, qrCurrentWidth / QR_SIZE_CHANGE_COEFFICIENT)
        }
    }

    fun keyBoardHide(qrCurrentWidth: Int) {
        if (maximizedQrSize != qrCurrentWidth) {
            resizeQrImageView(qrCurrentWidth, qrCurrentWidth * QR_SIZE_CHANGE_COEFFICIENT)
        }
    }

    private fun resizeQrImageView(width: Int, finalWidth: Int) {
        val anim = ValueAnimator.ofInt(width, finalWidth)
        anim.addUpdateListener {
            val animatedValue = it.animatedValue as Int
            qrCodeAnimatedValue.value = animatedValue
        }
        anim.duration = QR_ANIMATION_DURATION
        anim.start()
    }

    @Throws(WriterException::class)
    private fun generateQrBitmap(input: String): Bitmap {
        val hints = HashMap<EncodeHintType, String>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val qrCode = Encoder.encode(input, ErrorCorrectionLevel.H, hints)
        val byteMatrix = qrCode.matrix
        val width = byteMatrix.width + PADDING_SIZE
        val height = byteMatrix.height + PADDING_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y == 0 || y > byteMatrix.height || x == 0 || x > byteMatrix.width) {
                    bitmap.setPixel(x, y, QR_SECOND_COLOR)
                } else {
                    bitmap.setPixel(x, y, if (byteMatrix.get(x - PADDING_SIZE / 2, y - PADDING_SIZE / 2).toInt() == 1) Color.BLACK else QR_SECOND_COLOR)
                }
            }
        }
        return Bitmap.createScaledBitmap(bitmap, RECEIVE_QR_SCALE_SIZE, RECEIVE_QR_SCALE_SIZE, false)
    }

    fun shareQr(amount: String) {
        disposables.add(
            interactor.getAccountId()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ accountId ->
                    ShareUtil.openShareDialogWithBitmap(
                        router as AppCompatActivity,
                        resourceManager.getString(R.string.share_title),
                        generateMessage(amount, accountId),
                        qrBitmapLiveData.value!!
                    )
                }, {
                    onError(it)
                })
        )
    }

    private fun generateMessage(amount: String, accountId: String): String {
        var message = resourceManager.getString(R.string.qr_share_message_template)
        if (amount.isNotEmpty()) {
            message += " $amount"
        }

        message += " ${Const.XOR_ASSET_ID.split("#")[0].toUpperCase()}:\n$accountId"
        return message
    }
}