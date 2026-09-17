package com.github.honqout.tvlauncher3.utils

import android.view.View
import android.view.Window

class UIUtils {
    companion object {
        fun handleSystemBarsVisibility(window: Window, visible: Boolean) {
            // 传统 systemUiVisibility flags, 兼容低版本(Android 6), 隐藏系统状态栏时间等
            window.decorView.systemUiVisibility = if (visible) {
                View.SYSTEM_UI_FLAG_VISIBLE
            } else {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }
}