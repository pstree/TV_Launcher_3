package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.app.Application
import android.app.WallpaperManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()

    data class WallpaperApiResponse(val data: List<WallpaperItem>? = null)
    data class WallpaperItem(val url: String? = null)

    companion object {
        private const val TAG: String = "WallpaperViewModel"
        private const val API_URL: String = "https://wallpaper.ur1.fun/api/?cid=360new&start=%d&count=1"
        private const val WALLPAPER_INTERVAL_MS: Long = 5 * 60 * 1000L
        private const val TOTAL_WALLPAPERS: Int = 180
        private const val TIMEOUT_MS: Int = 10_000
    }

    init {
        startWallpaperLoop()
    }

    private fun startWallpaperLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { updateWallpaper() }
                    .onFailure { Log.e(TAG, "Failed to update wallpaper.", it) }
                delay(WALLPAPER_INTERVAL_MS)
            }
        }
    }

    private fun updateWallpaper() {
        val imageUrl = fetchRandomImageUrl() ?: return
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to download wallpaper. Response code: ${connection.responseCode}")
                return
            }
            connection.inputStream.use { inputStream ->
                val wallpaperManager = WallpaperManager.getInstance(getApplication())
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    wallpaperManager.setStream(
                        inputStream,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM
                    )
                } else {
                    wallpaperManager.setStream(inputStream)
                }
            }
            Log.i(TAG, "Wallpaper updated: $imageUrl")
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchRandomImageUrl(): String? {
        val start = Random.nextInt(TOTAL_WALLPAPERS)
        val connection = (URL(String.format(API_URL, start)).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to request wallpaper API. Response code: ${connection.responseCode}")
                return null
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val url = gson.fromJson(responseBody, WallpaperApiResponse::class.java)
                .data?.firstOrNull()?.url
            url
                ?.replace("http://", "https://")
                ?.replace("bdr/__85", "bdm/1920_1080_85")
        } finally {
            connection.disconnect()
        }
    }
}
