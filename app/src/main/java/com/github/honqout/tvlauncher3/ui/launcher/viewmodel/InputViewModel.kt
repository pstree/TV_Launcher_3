package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.tv.TvInputInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.utils.TvUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InputViewModel(application: Application) : AndroidViewModel(application) {
    // UI-related
    private var oldConfig: Configuration? = null
    private val _focusedItemIndex = MutableStateFlow<Int>(0)
    val focusedItemIndex: StateFlow<Int> = _focusedItemIndex.asStateFlow()

    // data-related
    private val _topBarHeight = MutableStateFlow<Int>(0)
    val topBarHeight: StateFlow<Int> = _topBarHeight.asStateFlow()
    private val _tvInputList = MutableStateFlow<List<TvInputInfo>>(emptyList())
    val tvInputList: StateFlow<List<TvInputInfo>> = _tvInputList.asStateFlow()

    companion object {
        const val TAG: String = "InputViewModel"
    }

    init {
        loadTvInputList()
    }

    override fun onCleared() {
        viewModelScope.cancel()
        super.onCleared()
    }

    fun loadTvInputList() {
        viewModelScope.launch(Dispatchers.Default) {
            _tvInputList.value = TvUtils.getTvInputList(getApplication())
        }
    }

    fun onConfigChanged(newConfig: Configuration) {
        oldConfig?.let {
            val diff = it.diff(newConfig)
            if ((diff and ActivityInfo.CONFIG_LOCALE) != 0) {
                loadTvInputList()
            }
        }
        oldConfig = newConfig
    }

    fun setTopBarHeight(newValue: Int) {
        _topBarHeight.update {
            newValue
        }
    }

    fun setFocusedItemIndex3(newValue: Int) {
        _focusedItemIndex.update {
            newValue
        }
    }

    fun getSelectedTvInputInfo(): TvInputInfo? {
        return if (_focusedItemIndex.value >= 0 && _focusedItemIndex.value < _tvInputList.value.size) {
            _tvInputList.value[_focusedItemIndex.value]
        } else {
            null
        }
    }
}