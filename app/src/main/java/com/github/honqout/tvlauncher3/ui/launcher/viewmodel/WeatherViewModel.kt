package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.data.WeatherNow
import com.github.honqout.tvlauncher3.data.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页左上角时钟旁边的天气。真正拉数据的是 [WeatherRepository],这里只负责在合适的时机触发一次,
 * 并且把读数暴露给界面。
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    companion object {
        private const val TAG: String = "WeatherViewModel"
    }

    val weather: StateFlow<WeatherNow?> = weatherRepository.weather

    init {
        refresh()
    }

    /** 同一个小时内重复调用只会命中缓存,可以放心在回到首页时调用。 */
    fun refresh() {
        viewModelScope.launch {
            try {
                weatherRepository.refreshIfStale()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh the weather.", e)
            }
        }
    }
}
