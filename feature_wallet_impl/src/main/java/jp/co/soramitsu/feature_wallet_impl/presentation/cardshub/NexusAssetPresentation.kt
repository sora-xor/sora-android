package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.QrCodeGenerator

/**
 * What the XOR pages of SORA Nexus and Taira need besides the wallet state, done the way the
 * SORA2 pages do it: an address's QR code and avatar, the file a QR code is shared from with its
 * message, copying, and dates in the SORA2 transaction page's format.
 */
class NexusAssetPresentation @Inject constructor(
    private val clipboardManager: BasicClipboardManager,
    private val avatarGenerator: AccountAvatarGenerator,
    private val qrCodeGenerator: QrCodeGenerator,
    private val fileManager: FileManager,
    private val resourceManager: ResourceManager,
    private val dateTimeFormatter: DateTimeFormatter,
) {

    fun copy(text: String) = clipboardManager.addToClipboard(text)

    /** The QR code a sender scans: the address alone, as the address is shown. */
    fun qrCode(address: String): Bitmap = qrCodeGenerator.generateQrBitmap(address)

    /**
     * The address's avatar, drawn from its public key as SORA2 draws an account's. Null when the
     * text is not an address of [network].
     */
    fun avatar(network: WalletNetworkId, address: String): Drawable? = try {
        val publicKey = IrohaAddressCodec.parse(
            address,
            NexusNetworks.require(network).chainDiscriminant,
        ).publicKeyHex
        avatarGenerator.createAvatarFromPublicKey(publicKey, AVATAR_SIZE_DP)
    } catch (_: Exception) {
        null
    }

    fun shareFile(qrCode: Bitmap): Uri = fileManager.writeExternalCacheBitmap(
        bitmap = qrCode,
        fileName = QR_CODE_FILE_NAME,
        format = Bitmap.CompressFormat.PNG,
        quality = 100,
    )

    /** "Scan this QR to send me XOR. My Taira Network Account ID: …", as SORA2 shares. */
    fun shareMessage(networkName: String, address: String): String =
        resourceManager.getString(R.string.wallet_qr_share_message_empty_template_v2)
            .format(XOR, networkName, address)

    fun dateTime(timestampMillis: Long): String =
        dateTimeFormatter.formatDate(Date(timestampMillis), DateTimeFormatter.DD_MMM_YYYY_HH_MM)

    internal companion object {
        const val XOR = "XOR"
        const val AVATAR_SIZE_DP = 32
        const val QR_CODE_FILE_NAME = "qrcodefile.png"
    }
}
