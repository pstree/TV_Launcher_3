package com.github.honqout.tvlauncher3.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.data.WeatherKind
import com.github.honqout.tvlauncher3.data.WeatherNow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间用大号字,日期和天气各小一号,三行共用一条左边缘。 */
private val TIME_FONT_SIZE = 46.sp
private val DATE_FONT_SIZE = 18.sp
private val WEATHER_FONT_SIZE = 16.sp

/** 壁纸可能是亮的照片,统一加一层柔和阴影,保证任何壁纸下都读得清。 */
private val CLOCK_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 2f),
    blurRadius = 14f
)

/**
 * 首页左上角的时钟:日期在最上,时间用大号字压在中间,天气垫在下面。
 *
 * 天气拿不到时只显示日期和时间,不会留一行空白;秒数与系统时间对齐(每整秒刷新一次),
 * 避免每秒扫两三次表带来的顿挫。
 */
@Composable
fun HomeClock(
    weather: WeatherNow?,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }

    val locale = Locale.getDefault()
    val dateText = remember(locale) {
        SimpleDateFormat(if (locale.language == "zh") "M月d日 EEEE" else "EEE, MMM d", locale)
    }.format(Date(now))
    val timeText = remember(locale) {
        SimpleDateFormat("HH:mm:ss", locale)
    }.format(Date(now))

    Column(modifier = modifier) {
        Text(
            text = dateText,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = DATE_FONT_SIZE,
            fontWeight = FontWeight.Medium,
            style = TextStyle(shadow = CLOCK_SHADOW),
            maxLines = 1
        )

        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = timeText,
            color = Color.White,
            fontSize = TIME_FONT_SIZE,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp,
            style = TextStyle(shadow = CLOCK_SHADOW),
            maxLines = 1
        )

        if (weather != null) {
            val condition = stringResource(conditionName(weather.kind))
            val range = if (weather.high != null && weather.low != null) {
                stringResource(R.string.weather_high_low, weather.high, weather.low)
            } else {
                null
            }
            val line = listOfNotNull(
                weather.place,
                "$condition ${weather.celsius}°",
                range
            ).joinToString("   ")

            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = line,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = WEATHER_FONT_SIZE,
                style = TextStyle(shadow = CLOCK_SHADOW),
                maxLines = 1
            )
        }
    }
}

private fun conditionName(kind: WeatherKind): Int = when (kind) {
    WeatherKind.CLEAR -> R.string.weather_clear
    WeatherKind.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherKind.CLOUDY -> R.string.weather_cloudy
    WeatherKind.FOG -> R.string.weather_fog
    WeatherKind.RAIN -> R.string.weather_rain
    WeatherKind.SNOW -> R.string.weather_snow
    WeatherKind.THUNDER -> R.string.weather_thunder
}
