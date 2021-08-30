package jp.co.soramitsu.common.util.ext

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Fragment.showBrowser(link: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
    startActivity(intent)
}

fun Fragment.dp2px(dp: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        requireContext().resources.displayMetrics
    ).toInt()

fun Fragment.dpRes2px(@DimenRes res: Int): Int =
    requireContext().resources.getDimensionPixelSize(res)

fun <T : Parcelable> Fragment.requireParcelable(key: String): T {
    return requireNotNull(requireArguments().getParcelable(key), { "Argument [$key] not found" })
}

fun Fragment.runDelayed(
    durationInMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit,
): Job = viewLifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
    delay(durationInMillis)
    block.invoke()
}
