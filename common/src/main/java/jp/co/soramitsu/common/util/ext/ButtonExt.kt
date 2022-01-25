/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.view.MotionEvent
import android.widget.Button
import soup.neumorphism.NeumorphButton
import soup.neumorphism.NeumorphImageButton
import soup.neumorphism.ShapeType

fun Button.enable() {
    this.isEnabled = true
    this.alpha = 1.0f
}

fun Button.disable() {
    this.isEnabled = false
    this.alpha = 0.5f
}

fun Button.enableIf(f: Boolean) {
    if (f) this.enable() else this.disable()
}

fun NeumorphButton.setShapeTypeTouchListener() {
    this.setOnTouchListener { view, motionEvent ->
        this.setShapeType(
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> ShapeType.PRESSED
                MotionEvent.ACTION_UP -> ShapeType.DEFAULT
                else -> ShapeType.PRESSED
            }
        )

        return@setOnTouchListener false
    }
}

fun NeumorphImageButton.setShapeTypeTouchListener() {
    this.setOnTouchListener { view, motionEvent ->
        this.setShapeType(
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> ShapeType.PRESSED
                MotionEvent.ACTION_UP -> ShapeType.DEFAULT
                else -> ShapeType.PRESSED
            }
        )

        return@setOnTouchListener false
    }
}
