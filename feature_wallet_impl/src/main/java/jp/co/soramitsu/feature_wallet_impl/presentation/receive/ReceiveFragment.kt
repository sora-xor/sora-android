/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentReceiveBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import java.io.File
import java.io.FileOutputStream

class ReceiveFragment : BaseFragment<ReceiveViewModel>(R.layout.fragment_receive) {

    companion object {
        private const val IMAGE_NAME = "image.png"
        private const val ARG_ASSET = "arg_asset"
        fun createBundle(asset: ReceiveAssetModel) = Bundle().apply {
            putSerializable(ARG_ASSET, asset)
        }
    }

    private val model: ReceiveAssetModel by lazy { requireArguments().getSerializable(ARG_ASSET) as ReceiveAssetModel }
    private val viewBinding by viewBinding(FragmentReceiveBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .receiveAmountComponentBuilder()
            .withFragment(this)
            .withReceiveModel(model)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        with(viewBinding.tbReceive) {
            setTitle(composeTitle())
            setHomeButtonListener {
                viewModel.backButtonPressed()
            }
            setRightActionClickListener { viewModel.shareQr() }
        }
        viewBinding.ivCopyAddress.setOnClickListener {
            viewModel.copyAddress()
        }
        viewBinding.ivQr.setOnClickListener {
            viewModel.copyAddress()
        }
        initListeners()
    }

    private fun initListeners() {
        viewModel.qrBitmapLiveData.observe(viewLifecycleOwner) {
            viewBinding.ivQr.setImageBitmap(it)
        }

        viewModel.shareQrCodeLiveData.observe(viewLifecycleOwner) {
            val mediaStorageDir: File = saveToTempFile(requireContext(), it.first)

            val imageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireActivity().packageName}.provider",
                mediaStorageDir
            )

            if (imageUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.common_share))
                    putExtra(Intent.EXTRA_TEXT, it.second)
                }

                startActivity(Intent.createChooser(intent, getString(R.string.common_share)))
            }
        }

        viewModel.userNameAddress.observe(viewLifecycleOwner) {
            if (it.second.isEmpty()) {
                viewBinding.tvUserName.text = it.first
                viewBinding.tvUserAddress.gone()
            } else {
                viewBinding.tvUserName.text = it.second
                viewBinding.tvUserAddress.show()
                viewBinding.tvUserAddress.text = it.first
            }
        }

        viewModel.userAvatar.observe(viewLifecycleOwner) {
            viewBinding.ivUserAvatar.setImageDrawable(it)
        }

        viewModel.copiedAddressEvent.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToTempFile(context: Context, bitmap: Bitmap): File {
        val mediaStorageDir = File(context.externalCacheDir!!.absolutePath + IMAGE_NAME)
        val outputStream = FileOutputStream(mediaStorageDir)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()
        return mediaStorageDir
    }

    private fun composeTitle() = listOf(
        getString(R.string.common_receive),
        model.networkName,
        getString(R.string.brackets_format, model.tokenName)
    ).filter { it.isNotEmpty() }.joinToString(separator = " ")
}
