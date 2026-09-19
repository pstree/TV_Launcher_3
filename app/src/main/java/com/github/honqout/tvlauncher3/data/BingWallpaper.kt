package com.github.honqout.tvlauncher3.data

import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 一张必应壁纸。
 *
 * 接口返回的 `url` 是必应图床上的相对路径,顺带记录了两种尺寸:
 * 列表用缩略图(接口那侧的 th 服务自己缩放)和原图(下载并设为系统壁纸用)。
 */
data class WallpaperItem(
    /** 壁纸日期(yyyyMMdd) */
    val date: String,
    val title: String,
    val copyright: String,
    /** 原图 1920x1080:直接字节流交给 WallpaperManager,不裁剪、不转码 */
    val imageUrl: String,
    /** 缩略图 512x288:列表用,一张 30KB 左右 */
    val thumbnailUrl: String,
    /** 保存到 Pictures/Wallpapers 时的文件名 */
    val fileName: String
) {
    val description: String
        get() = title.ifEmpty { copyright }
}

class BingWallpaper {
    companion object {
        private const val TAG: String = "BingWallpaper"

        /** 接口单次最多只返回 8 张,count 传更大也没用。 */
        const val CHUNK_SIZE: Int = 8

        private const val API_URL =
            "https://wallpaper.ur1.fun/api/?cid=bing&count=%d&start=%d"
        private const val IMAGE_HOST = "https://cn.bing.com"
        private const val THUMBNAIL_PARAMS = "&w=512&h=288&c=7&rs=1"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

        /**
         * 取一块壁纸。[start] 是相对最新一张的偏移量:0 表示从今天开始往前数,
         * 每块覆盖 start..start+7。偏移量超出服务端窗口时服务端会把它钳到边界,
         * 于是返回的内容会和上一块重叠——调用方按 url 去重即可,重复就说明到底了。
         */
        suspend fun fetchChunk(start: Int): List<WallpaperItem> = withContext(Dispatchers.IO) {
            val body = readText(String.format(Locale.US, API_URL, CHUNK_SIZE, start))
            parse(body)
        }

        /**
         * 打开原图字节流,调用方负责关闭。调用方需在 IO 线程执行。
         */
        fun openImageStream(item: WallpaperItem): InputStream = open(item.imageUrl).inputStream

        private fun parse(body: String): List<WallpaperItem> {
            val images = JSONObject(body).optJSONArray("images") ?: return emptyList()
            return (0 until images.length()).mapNotNull { index ->
                val json = images.optJSONObject(index) ?: return@mapNotNull null
                val path = json.optString("url")
                if (path.isEmpty()) {
                    return@mapNotNull null
                }
                val date = json.optString("startdate")
                val hash = json.optString("hsh")
                WallpaperItem(
                    date = date,
                    title = json.optString("title"),
                    copyright = json.optString("copyright"),
                    imageUrl = IMAGE_HOST + path,
                    thumbnailUrl = IMAGE_HOST + path + THUMBNAIL_PARAMS,
                    fileName = fileName(date, path, hash)
                )
            }
        }

        private fun readText(url: String): String = open(url).inputStream.use { input ->
            input.bufferedReader().use { it.readText() }
        }

        private fun open(url: String): HttpURLConnection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // 必应图床对没有 UA 的请求会直接拒绝
                setRequestProperty("User-Agent", USER_AGENT)
            }

        /**
         * bing_20260918_WinnatsPassPeak.jpg。
         * url 形如 /th?id=OHR.WinnatsPassPeak_EN-US6112068451_1920x1080.jpg&rf=...,
         * 取 id 里 "OHR." 之后、第一个 "_" 之前的那一段当名字,取不到就退回哈希值。
         */
        private fun fileName(date: String, path: String, hash: String): String {
            val id = path.substringAfter("id=", "").substringBefore('&')
            val slug = id.removePrefix("OHR.")
                .substringBefore('_')
                .replace(Regex("[^A-Za-z0-9]"), "")
                .ifEmpty { hash }
                .ifEmpty { "wallpaper" }
            val day = date.ifEmpty { hash }.ifEmpty { "unknown" }
            if (slug == "wallpaper") {
                Log.w(TAG, "Cannot derive the file name from $path")
            }
            return "bing_${day}_$slug.jpg"
        }
    }
}
