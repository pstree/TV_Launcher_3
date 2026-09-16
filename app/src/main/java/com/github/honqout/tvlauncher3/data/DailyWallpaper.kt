package com.github.honqout.tvlauncher3.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Keeps a daily wallpaper file up to date, sourced from the Bing daily image
 * feed (via wallpaper.ur1.fun). Returns the downloaded file, the last one that
 * succeeded when offline, or null when nothing has been downloaded yet.
 */
object DailyWallpaper {

    private const val API_URL = "https://wallpaper.ur1.fun/api/?cid=bing&start=-1&count=1"
    private const val IMAGE_HOST = "https://cn.bing.com"
    private const val FILE_NAME = "wallpaper_daily.jpg"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

    suspend fun ensureUpToDate(context: Context): File? = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, FILE_NAME)
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val today = dayFormat.format(Date())

        if (target.exists() && dayFormat.format(Date(target.lastModified())) == today) {
            return@withContext target
        }

        try {
            val imageUrl = fetchLatestImageUrl()
            val temp = File.createTempFile("wallpaper", ".jpg", context.cacheDir)
            downloadTo(imageUrl, temp)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target
        } catch (t: Throwable) {
            // Offline or the feed changed shape: keep yesterday's image if we have one.
            if (target.exists()) target else null
        }
    }

    private fun fetchLatestImageUrl(): String {
        val body = readText(API_URL)
        val url = JSONObject(body)
            .getJSONArray("images")
            .getJSONObject(0)
            .getString("url")
        return IMAGE_HOST + url
    }

    private fun readText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.inputStream.use { input ->
            return input.bufferedReader().use { it.readText() }
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
