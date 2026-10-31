package com.github.honqout.tvlauncher3.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

/** WMO 天气代码归并后的几种天气,只用来挑一句文案。 */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

/**
 * 首页左上角天气。
 *
 * @param celsius 当前温度(摄氏度)
 * @param isDay 白天/夜里,只影响图标与文案的细微差别
 * @param high [low] 当天最高/最低温度,接口没给时为 null
 * @param place 城市名(本地语言),拿不到时为 null
 */
data class WeatherNow(
    val celsius: Int,
    val kind: WeatherKind,
    val isDay: Boolean = true,
    val high: Int? = null,
    val low: Int? = null,
    val place: String? = null
)

/**
 * 首页时钟旁边的天气,数据来自 Open-Meteo:不需要 key,国内可直连。
 *
 * 位置用 IP 粗定位(ipinfo.io,失败再试 ipwho.is),城市名再交给 Open-Meteo 的地理编码
 * 换成本地语言("Xi'an" -> "西安")。定位结果缓存一天,天气每小时最多拉一次,失败十分钟后重试;
 * 上一次的读数落盘,冷启动先把旧读数显示出来,不至于空着。
 */
class WeatherRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG: String = "WeatherRepository"
        private const val REFRESH_MS = 60 * 60 * 1000L
        private const val RETRY_MS = 10 * 60 * 1000L
        private const val RELOCATE_MS = 24 * 60 * 60 * 1000L
        /** 超过这个时间的读数不再显示,宁可空着也不显示错的天。 */
        private const val STALE_MS = 3 * 60 * 60 * 1000L
        private const val NONE = Int.MIN_VALUE
    }

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val _weather = MutableStateFlow(readCached())
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

    private val mutex = Mutex()
    private var lastAttempt = 0L
    private var lastFailed = false

    /** 按缓存策略刷新:正常一小时一次,失败十分钟后重试,[force] 时立即拉取。 */
    suspend fun refreshIfStale(force: Boolean = false) {
        mutex.withLock {
            val since = System.currentTimeMillis() - lastAttempt
            val interval = if (lastFailed) RETRY_MS else REFRESH_MS
            if (!force && since < interval) {
                return@withLock
            }
            lastAttempt = System.currentTimeMillis()
            val reading = withContext(Dispatchers.IO) {
                runCatching { fetch() }
                    .onFailure { Log.w(TAG, "Weather unavailable.", it) }
                    .getOrNull()
            }
            lastFailed = reading == null
            if (reading != null) {
                _weather.value = reading
                cache(reading)
            }
        }
    }

    private fun fetch(): WeatherNow {
        var lat = prefs.getFloat("lat", Float.NaN).toDouble()
        var lon = prefs.getFloat("lon", Float.NaN).toDouble()
        var place = prefs.getString("place", null)
        val locatedAt = prefs.getLong("located_at", 0L)
        if (lat.isNaN() || lon.isNaN() ||
            System.currentTimeMillis() - locatedAt > RELOCATE_MS
        ) {
            val located = locate()
            lat = located.lat
            lon = located.lon
            place = located.place
            prefs.edit()
                .putFloat("lat", lat.toFloat())
                .putFloat("lon", lon.toFloat())
                .putString("place", place)
                .putLong("located_at", System.currentTimeMillis())
                .apply()
        } else if (Locale.getDefault().language == "zh" && place?.all { it.code < 128 } == true) {
            // 之前那次地理编码没成功,缓存里留的是 ipinfo 的英文名("Xi'an");每次刷新再试一次。
            localName(place)?.takeIf { it != place }?.let { localized ->
                place = localized
                prefs.edit().putString("place", localized).apply()
            }
        }
        Log.i(TAG, "Weather for $place ($lat, $lon).")

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val daily = json.optJSONObject("daily")

        fun today(key: String): Int? = daily
            ?.optJSONArray(key)
            ?.optDouble(0)
            ?.takeIf { !it.isNaN() }
            ?.roundToInt()

        return WeatherNow(
            celsius = current.getDouble("temperature_2m").roundToInt(),
            kind = kindOf(current.optInt("weather_code", 0)),
            isDay = current.optInt("is_day", 1) == 1,
            high = today("temperature_2m_max"),
            low = today("temperature_2m_min"),
            place = place
        )
    }

    private data class Located(val lat: Double, val lon: Double, val place: String?)

    private fun locate(): Located {
        // ipinfo.io:免费、不用 key,loc 是 "lat,lon",city 是英文名。
        val primary = runCatching {
            val json = JSONObject(get("https://ipinfo.io/json"))
            val loc = json.getString("loc").split(',')
            Located(
                lat = loc[0].toDouble(),
                lon = loc[1].toDouble(),
                place = localName(json.optString("city").ifBlank { null })
            )
        }.onFailure { Log.w(TAG, "ipinfo.io failed, trying ipwho.is.", it) }.getOrNull()
        if (primary != null) {
            return primary
        }
        val backup = JSONObject(get("https://ipwho.is/"))
        return Located(
            lat = backup.getDouble("latitude"),
            lon = backup.getDouble("longitude"),
            place = localName(backup.optString("city").ifBlank { null })
        )
    }

    /** 把英文城市名换成本地语言的名字;查不到就保留原名。 */
    private fun localName(city: String?): String? {
        if (city.isNullOrBlank()) {
            return null
        }
        val language = if (Locale.getDefault().language == "zh") "zh" else "en"
        return runCatching {
            val query = URLEncoder.encode(city, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query" +
                    "&count=1&language=$language&format=json"
            JSONObject(get(url))
                .optJSONArray("results")
                ?.optJSONObject(0)
                ?.optString("name")
                ?.ifBlank { null }
                ?: city
        }.getOrDefault(city)
    }

    private fun cache(weather: WeatherNow) {
        prefs.edit()
            .putLong("fetched_at", System.currentTimeMillis())
            .putInt("celsius", weather.celsius)
            .putString("kind", weather.kind.name)
            .putBoolean("day", weather.isDay)
            .putInt("high", weather.high ?: NONE)
            .putInt("low", weather.low ?: NONE)
            .apply()
    }

    private fun readCached(): WeatherNow? {
        if (System.currentTimeMillis() - prefs.getLong("fetched_at", 0L) > STALE_MS) {
            return null
        }
        val kind = runCatching {
            WeatherKind.valueOf(prefs.getString("kind", "").orEmpty())
        }.getOrNull() ?: return null
        return WeatherNow(
            celsius = prefs.getInt("celsius", 0),
            kind = kind,
            isDay = prefs.getBoolean("day", true),
            high = prefs.getInt("high", NONE).takeIf { it != NONE },
            low = prefs.getInt("low", NONE).takeIf { it != NONE },
            place = prefs.getString("place", null)
        )
    }

    /** WMO 天气代码,和 Open-Meteo 的约定一致。 */
    private fun kindOf(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.THUNDER
        else -> WeatherKind.RAIN
    }

    /**
     * 老盒子的系统 CA 库可能不认这几个域名的证书链,它们都支持明文 HTTP,握手失败时退回 HTTP
     * (只对 network_security_config 里白名单的域名放开)。
     */
    private fun get(url: String): String = try {
        read(url)
    } catch (e: SSLException) {
        Log.i(TAG, "TLS failed, retrying over HTTP: $url", e)
        read(url.replaceFirst("https://", "http://"))
    }

    private fun read(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }
}
