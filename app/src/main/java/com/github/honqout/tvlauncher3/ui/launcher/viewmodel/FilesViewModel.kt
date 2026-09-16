package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {

    data class FileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val isVolume: Boolean = false
    )

    private val _topBarHeight = MutableStateFlow<Int>(0)
    val topBarHeight: StateFlow<Int> = _topBarHeight.asStateFlow()

    private val _currentDir = MutableStateFlow<File?>(null)
    val currentDir: StateFlow<File?> = _currentDir.asStateFlow()

    private val _items = MutableStateFlow<List<FileItem>>(emptyList())
    val items: StateFlow<List<FileItem>> = _items.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    companion object {
        private const val TAG: String = "FilesViewModel"
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun setShowPermissionDialog(newValue: Boolean) {
        _showPermissionDialog.update { newValue }
    }

    fun setTopBarHeight(newValue: Int) {
        _topBarHeight.update { newValue }
    }

    fun onConfigChanged(newConfig: Configuration) {
        if (!hasAllFilesAccess()) {
            setShowPermissionDialog(true)
        }
        refresh()
    }

    fun refresh() {
        val currentDirValue = _currentDir.value
        if (currentDirValue == null) {
            loadVolumes()
        } else {
            loadDirectory(currentDirValue)
        }
    }

    fun onItemClick(item: FileItem) {
        val file = File(item.path)
        if (item.isDirectory) {
            _currentDir.update { file }
            refresh()
        } else {
            openFile(file)
        }
    }

    fun goUp() {
        val parent = _currentDir.value?.parentFile
        if (parent != null) {
            _currentDir.update { parent }
        } else {
            _currentDir.update { null }
        }
        refresh()
    }

    private fun loadVolumes() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val volumeList = mutableListOf<FileItem>()
            val primaryDir = Environment.getExternalStorageDirectory()
            volumeList.add(
                FileItem(
                    name = context.getString(R.string.internal_storage),
                    path = primaryDir.absolutePath,
                    isDirectory = true,
                    isVolume = true
                )
            )
            runCatching {
                appendRemovableVolumes(context, primaryDir.absolutePath, volumeList)
            }.onFailure {
                Log.e(TAG, "Failed to load storage volumes.", it)
            }
            _items.value = volumeList
        }
    }

    /**
     * Append every storage volume except the primary one.
     */
    private fun appendRemovableVolumes(
        context: Application,
        primaryPath: String,
        out: MutableList<FileItem>
    ) {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return
        val volumes = storageManager.storageVolumes
        // The returned paths are ordered consistently with StorageManager.getStorageVolumes().
        val externalFilesDirs = context.getExternalFilesDirs(null).filterNotNull()
        volumes.forEachIndexed { index, volume ->
            val root = resolveVolumeRoot(volume, externalFilesDirs.getOrNull(index))
                ?: return@forEachIndexed
            if (root == primaryPath) {
                return@forEachIndexed
            }
            out.add(
                FileItem(
                    name = volume.getDescription(context),
                    path = root,
                    isDirectory = true,
                    isVolume = true
                )
            )
        }
    }

    /**
     * Resolve the root directory of a storage volume. [StorageVolume.getDirectory] is only
     * available from API 30, so on older releases the root is derived from the public per-volume
     * external files directory (`/storage/XXXX-XXXX/Android/data/<package>/files`).
     */
    private fun resolveVolumeRoot(volume: StorageVolume, externalFilesDir: File?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return volume.directory?.absolutePath
        }
        val path = externalFilesDir?.absolutePath ?: return null
        return path.substringBefore("/Android/").ifEmpty { null }
    }

    private fun loadDirectory(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = runCatching { dir.listFiles() }.getOrNull() ?: emptyArray()
            val itemList = files
                .map { file ->
                    FileItem(
                        name = file.name.ifEmpty { file.absolutePath },
                        path = file.absolutePath,
                        isDirectory = file.isDirectory
                    )
                }
                .sortedWith(
                    compareByDescending<FileItem> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            _items.value = itemList
        }
    }

    private fun openFile(file: File) {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Log.e(TAG, "Failed to open file: ${file.absolutePath}", it)
        }
    }
}
