/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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
