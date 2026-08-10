package com.github.honqout.tvlauncher3.utils

import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class UIUtils {
    companion object {
        fun handleSystemBarsVisibility(window: Window, visible: Boolean) {
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                if (visible) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    controller.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
}