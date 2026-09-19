package com.github.honqout.tvlauncher3.ui.launcher.viewmodel

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.honqout.tvlauncher3.R
import com.github.honqout.tvlauncher3.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * 各个卷(内置存储、U 盘)的根路径。卷列表就是文件页的顶层,
     * 返回键不允许越过卷根上到 /storage 甚至 /。
     */
    private val _volumeRoots = MutableStateFlow<List<String>>(emptyList())

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    /** 复制到剪贴板待粘贴的文件/文件夹(只记路径,不预读内容)。 */
    private val _clipboard = MutableStateFlow<FileItem?>(null)
    val clipboard: StateFlow<FileItem?> = _clipboard.asStateFlow()

    companion object {
        private const val TAG: String = "FilesViewModel"
        private const val APK_EXTENSION: String = "apk"
        private const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"
    }

    // 监听 U 盘/外置存储挂载与卸载,自动刷新卷列表;挂载时提示用户
    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_MEDIA_MOUNTED -> {
                    Toast.makeText(
                        getApplication(),
                        R.string.external_storage_found,
                        Toast.LENGTH_LONG
                    ).show()
                    _currentDir.update { null }
                    refresh()
                }

                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_REMOVED -> {
                    // 若正在浏览的目录可能已被卸载,回到卷列表重新加载
                    _currentDir.update { null }
                    refresh()
                }
            }
        }
    }

    init {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addDataScheme("file")
            }
            ContextCompat.registerReceiver(
                getApplication(),
                mediaReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure {
            Log.e(TAG, "Failed to register media receiver.", it)
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(mediaReceiver) }
        super.onCleared()
    }

    fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        val readGranted = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Q: the write permission is deprecated, so READ is all that can be checked.
            return readGranted
        }
        // Android 6~9: reading and deleting both need the write permission as well.
        return readGranted && ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setShowPermissionDialog(newValue: Boolean) {
        _showPermissionDialog.update { newValue }
    }

    fun setTopBarHeight(newValue: Int) {
        _topBarHeight.update { newValue }
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
        when {
            item.isDirectory -> {
                _currentDir.update { file }
                refresh()
            }
            // 确认键点 APK 直接走系统安装器,其余文件交给默认应用打开
            file.extension.equals(APK_EXTENSION, ignoreCase = true) -> installApk(file)
            else -> openFile(file)
        }
    }

    /**
     * 把 [item] 放入剪贴板,真正的复制发生在粘贴时(与系统文件管理器一致)。
     */
    fun copyItem(item: FileItem) {
        _clipboard.value = item
    }

    /**
     * 把剪贴板里的文件/文件夹复制到当前目录,重名自动加 " (n)" 后缀,回调在主线程返回。
     */
    fun pasteItem(onResult: (Boolean) -> Unit) {
        val source = _clipboard.value
        val targetDir = _currentDir.value
        if (source == null || targetDir == null) {
            onResult(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { copyInto(File(source.path), targetDir) }.getOrDefault(false)
            if (ok) {
                refresh()
            }
            withContext(Dispatchers.Main) {
                onResult(ok)
            }
        }
    }

    /**
     * 递归复制 [source] 到 [targetDir];目录不允许复制进自身或后代目录,否则会无限递归。
     */
    private fun copyInto(source: File, targetDir: File): Boolean {
        if (!source.exists()) {
            Log.e(TAG, "Cannot paste ${source.absolutePath}: it no longer exists.")
            return false
        }
        if (source.isDirectory &&
            (targetDir.absolutePath + File.separator)
                .startsWith(source.absolutePath + File.separator)
        ) {
            Log.e(TAG, "Cannot paste ${source.absolutePath} into itself.")
            return false
        }
        val target = uniqueTarget(targetDir, source.name)
        return if (source.isDirectory) {
            source.copyRecursively(target, overwrite = false)
        } else {
            source.copyTo(target, overwrite = false)
            true
        }
    }

    /**
     * 重名时在扩展名前插入 " (n)":a.txt -> a (1).txt,保留下载多份同名文件的习惯。
     */
    private fun uniqueTarget(dir: File, name: String): File {
        val existing = File(dir, name)
        if (!existing.exists()) {
            return existing
        }
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(dir, "$base ($index)$extension")
            if (!candidate.exists()) {
                return candidate
            }
            index++
        }
    }

    fun goUp() {
        val currentDir = _currentDir.value ?: return
        val parent = currentDir.parentFile
        val parentPath = parent?.absolutePath
        // 只有在卷内部才逐级向上;已经到卷根(或更上层,例如 /storage、/)时回到卷列表
        val staysInsideVolume = parentPath != null && parent != null &&
            _volumeRoots.value.any { root -> isSameOrChild(parentPath, root) }
        if (staysInsideVolume) {
            _currentDir.update { parent }
        } else {
            _currentDir.update { null }
        }
        refresh()
    }

    /**
     * 删除文件或整个文件夹,成功后刷新列表,回调在主线程返回。
     */
    fun deleteItem(item: FileItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val file = File(item.path)
                if (item.isDirectory) file.deleteRecursively() else file.delete()
            }.getOrDefault(false)
            if (ok) {
                // 剪贴板里的路径已被删掉时清空,否则粘贴只会失败
                val clipboardPath = _clipboard.value?.path
                if (clipboardPath != null && isSameOrChild(clipboardPath, item.path)) {
                    _clipboard.value = null
                }
                refresh()
            }
            withContext(Dispatchers.Main) {
                onResult(ok)
            }
        }
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
                val sm = context.getSystemService(StorageManager::class.java)
                val vols = storageVolumes(context)
                Log.d(
                    TAG,
                    "volumes count=${vols.size} " +
                        "desc=${vols.joinToString { volumeDescription(context, it) }}"
                )
                appendRemovableVolumes(context, primaryDir.absolutePath, volumeList)
            }.onFailure {
                Log.e(TAG, "Failed to load storage volumes.", it)
            }
            _volumeRoots.value = volumeList.map { it.path }
            _items.value = volumeList
        }
    }

    /**
     * 兼容取存储卷列表:getStorageVolumes() 是 API 24+,Android 6(API 23) 用 getVolumeList()。
     * 新 compileSdk 已移除 getVolumeList,故 Android 6 上走反射调用(系统 API 不参与混淆)。
     */
    private fun storageVolumes(context: Context): List<StorageVolume> {
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return sm.storageVolumes
        }
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            StorageManager::class.java.getMethod("getVolumeList")
                .invoke(sm) as Array<StorageVolume>
        }.getOrElse { emptyArray() }.toList()
    }

    /**
     * 兼容取卷描述:getDescription(Context) 是 API 24+,Android 6 用无参版本(新 SDK 已移除,走反射)。
     */
    private fun volumeDescription(context: Context, volume: StorageVolume): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            volume.getDescription(context)
        } else {
            runCatching {
                // Looked up by name: StorageVolume itself only exists from API 24, and this branch
                // is the one that runs below it.
                Class.forName("android.os.storage.StorageVolume")
                    .getMethod("getDescription")
                    .invoke(volume) as String
            }.getOrElse { volume.toString() }
        }

    /**
     * Append every storage volume except the primary one.
     */
    private fun appendRemovableVolumes(
        context: Application,
        primaryPath: String,
        out: MutableList<FileItem>
    ) {
        val volumes = storageVolumes(context)
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
                    name = volumeDescription(context, volume),
                    path = root,
                    isDirectory = true,
                    isVolume = true
                )
            )
        }
        // 兜底:Android 6~7 上 getExternalFilesDirs 对 U 盘卷常返回 null,
        // 直接枚举 /storage/ 下实际存在的目录(排除主存储与 self)。
        // 不用 isDirectory 过滤:挂载异常时卷目录会返回 false,但仍应显示。
        if (out.size <= 1) {
            runCatching {
                File("/storage").listFiles()?.forEach { dir ->
                    val p = dir.absolutePath
                    val name = dir.name
                    if (name != "emulated" && name != "self" &&
                        p != primaryPath && out.none { it.path == p }
                    ) {
                        out.add(
                            FileItem(
                                name = dir.name,
                                path = p,
                                isDirectory = true,
                                isVolume = true
                            )
                        )
                    }
                }
            }.onFailure {
                Log.e(TAG, "Failed to enumerate /storage/.", it)
            }
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

    /**
     * 交给系统安装器安装 APK。这里不预先用 [PackageManager.canRequestPackageInstalls] 拦一道:
     * 该值在没设置过 app-op 的设备上不可靠(部分 TV 固件默认允许安装),误拦会让用户点不动 APK。
     * 只有真的起不来时才提示,并按 API 26+ 最常见的原因(本应用未被允许"安装未知应用")引导授权。
     */
    private fun installApk(file: File) {
        val context = getApplication<Application>()
        val started = runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.e(TAG, "Failed to install apk: ${file.absolutePath}", it)
        }.isSuccess
        if (started) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                R.string.install_permission_required,
                Toast.LENGTH_LONG
            ).show()
            IntentUtils.launchUnknownAppSourcesSettings(context, context.packageName)
        } else {
            Toast.makeText(context, R.string.install_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** [path] 是否等于 [parent] 或位于其下(用于剪贴板失效判断)。 */
    private fun isSameOrChild(path: String, parent: String): Boolean =
        path == parent || path.startsWith(parent + File.separator)
}
