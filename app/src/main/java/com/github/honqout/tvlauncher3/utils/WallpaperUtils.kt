package com.github.honqout.tvlauncher3.utils

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.github.honqout.tvlauncher3.data.BingWallpaper
import com.github.honqout.tvlauncher3.data.WallpaperItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 壁纸落盘与设置。全程按字节流转发,不解码、不裁剪、不重新编码。
 */
class WallpaperUtils {
    companion object {
        private const val TAG: String = "WallpaperUtils"
        private const val SUB_DIR: String = "Wallpapers"
        private const val MIME_TYPE: String = "image/jpeg"

        /** 启动器背景用的本地副本文件名(放在私有目录,不受用户删图影响)。 */
        private const val LAUNCHER_BACKGROUND_FILE: String = "launcher_wallpaper.jpg"

        /**
         * Android 10 起写真机公共图片目录走 MediaStore,不需要权限;
         * 更低版本只能直接写文件,需要 WRITE_EXTERNAL_STORAGE。
         */
        fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        /**
         * 把原图下载到 Pictures/Wallpapers 下,返回可用于 [WallpaperManager] 的 Uri,
         * 失败返回 null。
         */
        suspend fun saveToPictures(context: Context, item: WallpaperItem): Uri? =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveWithMediaStore(context, item)
                    } else {
                        saveWithFile(item)
                    }
                }.onFailure {
                    Log.e(TAG, "Cannot save ${item.fileName} to Pictures/$SUB_DIR", it)
                }.getOrNull()
            }

        /**
         * 把 [source] 复制一份作为启动器自己的背景。
         *
         * 启动器不再读系统壁纸(那套方案在电视盒子上又慢又不可靠),
         * 所以选中的图要在这里留一份本地副本,读取成本和内置壁纸完全一样。
         */
        suspend fun setLauncherBackground(context: Context, source: Uri): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.filesDir, LAUNCHER_BACKGROUND_FILE)
                    context.contentResolver.openInputStream(source)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open $source")
                    true
                }.onFailure {
                    Log.e(TAG, "Cannot set the launcher background from $source", it)
                }.getOrDefault(false)
            }

        /**
         * 解码启动器背景图;没设过(或解码失败)返回 null,调用方回退到内置壁纸。
         * 按视图尺寸做 2 的幂采样,避免整张 1080p 位图常驻内存。
         */
        suspend fun loadLauncherBackground(
            context: Context,
            viewWidth: Int,
            viewHeight: Int
        ): Bitmap? = withContext(Dispatchers.IO) {
            val file = File(context.filesDir, LAUNCHER_BACKGROUND_FILE)
            if (!file.exists()) {
                return@withContext null
            }
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        viewWidth,
                        viewHeight
                    )
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }.onFailure {
                Log.e(TAG, "Cannot decode ${file.absolutePath}", it)
            }.getOrNull()
        }

        private fun sampleSize(width: Int, height: Int, viewWidth: Int, viewHeight: Int): Int {
            if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0) {
                return 1
            }
            var sample = 1
            // 只缩不放:一直减半,直到再减就小于视图尺寸
            while (width / (sample * 2) >= viewWidth && height / (sample * 2) >= viewHeight) {
                sample *= 2
            }
            return sample
        }

        /**
         * 把图片设为系统壁纸,不做任何额外处理。
         */
        suspend fun applyWallpaper(context: Context, uri: Uri): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    val manager = WallpaperManager.getInstance(context)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            manager.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM)
                        } else {
                            @Suppress("DEPRECATION")
                            manager.setStream(input)
                        }
                        true
                    } ?: false
                }.onFailure {
                    Log.e(TAG, "Cannot set the wallpaper from $uri", it)
                }.getOrDefault(false)
            }

        private fun saveWithFile(item: WallpaperItem): Uri? {
            @Suppress("DEPRECATION")
            val pictures = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val dir = File(pictures, SUB_DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Cannot create ${dir.absolutePath}")
                return null
            }
            val target = File(dir, item.fileName)
            BingWallpaper.openImageStream(item).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return target.toUri()
        }

        private fun saveWithMediaStore(context: Context, item: WallpaperItem): Uri? {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, item.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SUB_DIR")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val written = runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    BingWallpaper.openImageStream(item).use { input -> input.copyTo(output) }
                } ?: error("Cannot open the output stream of $uri")
            }.onFailure {
                Log.e(TAG, "Cannot write $uri", it)
                resolver.delete(uri, null, null)
            }.isSuccess
            if (!written) {
                return null
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            return uri
        }
    }
}
