package jp.co.soramitsu.common.presentation.view

import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation

object ViewAnimations {
    val rotateAnimation: AnimationSet
        get() {
            val animRotate = RotateAnimation(
                0.0f, 359.0f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f
            )
            val animSet = AnimationSet(true)
            animSet.interpolator = LinearInterpolator()
            animRotate.duration = 1600
            animRotate.repeatCount = Animation.INFINITE
            animRotate.repeatMode = Animation.INFINITE
            animSet.addAnimation(animRotate)
            return animSet
        }
}
