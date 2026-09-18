package com.github.honqout.tvlauncher3.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette

class DrawableUtils {
    companion object {
        private const val TAG: String = "DrawableUtils"

        /**
         * Raster size used when a drawable reports no intrinsic size. Adaptive icons and layer
         * drawables report -1, and rasterising those at 1x1 would make the dominant colour a
         * single arbitrary pixel.
         */
        private const val FALLBACK_RASTER_SIZE = 192

        fun toBitmap(drawable: Drawable): Bitmap {
            return when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_RASTER_SIZE
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_RASTER_SIZE
                    val bitmap = createBitmap(width, height)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                    return bitmap
                }
            }
        }

        fun getIconForeground(drawable: Drawable): Drawable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
                return drawable.foreground
            }
            return drawable
        }

        @ColorInt
        fun getDominantColor(drawable: Drawable, defaultColor: Int = Color.TRANSPARENT): Int {
            val bitmap = toBitmap(drawable)
            val palette = Palette.from(bitmap).generate()
            return palette.getDominantColor(defaultColor)
        }

        fun getBackgroundColorFromAppIcon(
            drawable: Drawable,
            defaultColor: Int = Color.TRANSPARENT
        ): Int {
            val backgroundColor = when (drawable) {
                is LayerDrawable -> {
                    getBackgroundColorFromLayerDrawable(drawable)
                }

                is ColorDrawable -> {
                    drawable.color
                }

                is GradientDrawable -> {
                    getColorFromGradientDrawable(drawable)
                }

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
                        getBackgroundColorFromAdaptiveIcon(drawable)
                    } else {
                        getDominantColor(drawable, defaultColor)
                    }
                }
            }
            return if (backgroundColor == null || backgroundColor == Color.TRANSPARENT) {
                getDominantColor(drawable, defaultColor)
            } else {
                backgroundColor
            }
        }

        private fun getBackgroundColorFromLayerDrawable(layerDrawable: LayerDrawable): Int? {
            for (i in 0 until layerDrawable.numberOfLayers) {
                val color = when (val drawable = layerDrawable.getDrawable(i)) {
                    is ColorDrawable -> drawable.color
                    is GradientDrawable -> getColorFromGradientDrawable(drawable)
                    else -> null
                }
                if (color != null) {
                    return color
                }
            }
            return null
        }

        private fun getColorFromGradientDrawable(gradientDrawable: GradientDrawable): Int? {
            // getColors()/getColor() are API 24+; below that the colour cannot be read, and the
            // caller falls back to the palette extraction.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return null
            }
            val colors = gradientDrawable.colors
            return if (colors != null && colors.size == 1) {
                colors[0]
            } else {
                gradientDrawable.color?.defaultColor
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun getBackgroundColorFromAdaptiveIcon(icon: AdaptiveIconDrawable): Int? {
            return when (val background = icon.background) {
                is ColorDrawable -> background.color
                is GradientDrawable -> getColorFromGradientDrawable(background)
                else -> getDominantColor(background)
            }
        }
    }
}