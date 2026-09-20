package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.data.BingWallpaper
import com.github.honqout.tvlauncher3.data.WallpaperItem
import com.github.honqout.tvlauncher3.utils.WallpaperUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class WallpaperViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {

    enum class ApplyResult {
        SUCCESS, SAVE_FAILED
    }

    companion object {
        /** 只展示一页:4 列 x 3 行 = 12 张。 */
        const val ITEM_COUNT: Int = 12

        /**
         * 接口每块最多给 8 张,凑够一页最多取几块。留这个上限只是防止接口行为变化时无限请求。
         */
        private const val MAX_CHUNKS: Int = 4
        private const val TAG: String = "WallpaperViewModel"

        /** 本地日期格式,和接口返回的 startdate 一致。 */
        private const val DATE_PATTERN: String = "yyyyMMdd"
    }

    /**
     * 上次成功拉取时的本地日期(yyyyMMdd),空表示还没成功过。
     *
     * 必应每天只换一张图,而启动器是常驻进程、不会随换图重建 ViewModel,
     * 所以用"日期"而不是时长来卡:同一天内反复进出壁纸页都不再请求。
     */
    private var lastLoadedDate: String = ""

    private val _items = MutableStateFlow<List<WallpaperItem>>(emptyList())
    val items: StateFlow<List<WallpaperItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    /** 每次成功换壁纸自增,MainActivity 据此重新加载启动器背景。 */
    private val _backgroundVersion = MutableStateFlow(0)
    val backgroundVersion: StateFlow<Int> = _backgroundVersion.asStateFlow()

    init {
        load()
    }

    /**
     * 进入壁纸页时调用。当天已经成功拉过就不再请求,跨天(或还没成功过)才重新拉取,
     * 这样必应换了图当天就能体现出来,而不用等进程重启。每天至多请求一次。
     */
    fun refreshIfStale() {
        if (lastLoadedDate == today()) {
            return
        }
        load()
    }

    private fun today(): String =
        SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())

    /**
     * 拉取一页壁纸。接口每块只给 8 张,所以按游标连续取,凑够 12 张就停。
     */
    fun load() {
        if (_loading.value) {
            return
        }
        _loading.value = true
        _loadFailed.value = false
        viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) { fetchWallpapers() }
            if (fetched.isEmpty()) {
                _loadFailed.value = true
            } else {
                _items.value = fetched.take(ITEM_COUNT)
                lastLoadedDate = today()
            }
            _loading.value = false
        }
    }

    private suspend fun fetchWallpapers(): List<WallpaperItem> {
        val fetched = mutableListOf<WallpaperItem>()
        val seen = mutableSetOf<String>()
        var start = 0
        for (chunk in 0 until MAX_CHUNKS) {
            if (fetched.size >= ITEM_COUNT) {
                break
            }
            val items = runCatching { BingWallpaper.fetchChunk(start) }.getOrElse {
                Log.e(TAG, "Cannot fetch wallpapers at start=$start", it)
                emptyList()
            }
            // 重复(服务端已到历史边界)或彻底取不到,就到此为止
            val fresh = items.filter { seen.add(it.imageUrl) }
            if (fresh.isEmpty()) {
                break
            }
            fetched += fresh
            // 下一块的起点回退一张,和上一块重叠,避免服务端窗口错位漏数据
            start = fetched.size - 1
        }
        return fetched
    }

    /**
     * 下载原图存到 Pictures/Wallpapers、留一份副本做启动器背景,再设为系统壁纸。
     * 结果在主线程回调。
     */
    fun applyWallpaper(item: WallpaperItem, onResult: (ApplyResult) -> Unit) {
        if (_applying.value) {
            return
        }
        _applying.value = true
        viewModelScope.launch {
            val context = getApplication<Application>()
            val uri = WallpaperUtils.saveToPictures(context, item)
            val result = if (uri == null ||
                !WallpaperUtils.setLauncherBackground(context, uri)
            ) {
                ApplyResult.SAVE_FAILED
            } else {
                // 系统壁纸失败不致命:部分电视固件根本没有壁纸服务,启动器自己的背景已经换好了
                if (!WallpaperUtils.applyWallpaper(context, uri)) {
                    Log.w(TAG, "Cannot set the system wallpaper, keeping the launcher background.")
                }
                _backgroundVersion.value += 1
                ApplyResult.SUCCESS
            }
            _applying.value = false
            onResult(result)
        }
    }
}
