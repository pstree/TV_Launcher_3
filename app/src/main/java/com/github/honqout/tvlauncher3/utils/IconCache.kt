package com.github.honqout.tvlauncher3.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Process-lifetime, in-memory cache for resolved app icons and their dominant background color.
 *
 * Caching avoids re-decoding the icon bitmap from disk and re-running the color extraction
 * (Palette pass) every time the "Apps" tab is re-entered. Without it, each icon flashes because
 * the owning screen is torn down and rebuilt on every tab switch. Cached entries are surfaced
 * synchronously on the first frame of a rebuild, so there is nothing to "flash" into place.
 *
 * A simple LRU keeps the cache bounded: a launcher can list several hundred activities, and each
 * entry may hold a reasonably large icon/banner bitmap.
 */
object IconCache {

    /** A fully-resolved app icon together with the dominant color of its background. */
    data class IconEntry(
        val bitmap: ImageBitmap,
        val backgroundColor: Color
    )

    private const val MAX_SIZE = 200

    // accessOrder=true turns the LinkedHashMap into an LRU: reads move an entry to the tail and
    // the eldest (least recently used) entry is dropped when the cap is exceeded.
    private val cache = object : LinkedHashMap<String, IconEntry>(MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, IconEntry>?
        ): Boolean = size > MAX_SIZE
    }

    @Synchronized
    fun get(key: String): IconEntry? = cache[key]

    @Synchronized
    fun put(key: String, value: IconEntry) {
        cache[key] = value
    }
}