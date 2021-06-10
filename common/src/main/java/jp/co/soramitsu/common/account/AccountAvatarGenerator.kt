package jp.co.soramitsu.common.account

import android.graphics.drawable.PictureDrawable
import com.caverock.androidsvg.SVG
import jdenticon.Jdenticon
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import javax.inject.Singleton

@Singleton
class AccountAvatarGenerator(
    private val resourceManager: ResourceManager,
) {
    fun createAvatarFromKey(publicKey: String, sizeInDp: Int): PictureDrawable {
        val icon = Jdenticon.toSvg(publicKey, resourceManager.dp2px(sizeInDp))
        val svg = SVG.getFromString(icon)
        return PictureDrawable(svg.renderToPicture())
    }

    fun createAvatar(address: String, sizeInDp: Int): PictureDrawable =
        createAvatarFromKey(publicKey = address.toAccountId().toHexString(false), sizeInDp = sizeInDp)
}
