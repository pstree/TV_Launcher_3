# TV Launcher 3 — 长期记忆

## 项目定位
- Android TV 启动器（Home screen 替代品），Kotlin + Jetpack Compose + **androidx.tv.material3**（不是 mobile material3）。
- 单一 Gradle 模块 `:app`，根包 `com.github.honqout.tvlauncher3`，namespace = applicationId。
- minSdk 24 / compileSdk 37 / targetSdk 37，JVM 17，AGP 9.x + Kotlin 2.x + KSP + Hilt + Protobuf。

## 分层与架构约定（当前事实）
- `ui/launcher/activity` 只放 `MainActivity`（承载 3 个 tab 的 ViewModel 并向下传递）。
- `ui/launcher/screen` 放 Compose 屏幕，`ui/launcher/viewmodel` 放 Hilt ViewModel（全部 `@HiltViewModel` + `@Inject constructor`）。
- `components/{button,dialog,text,grid}` 放无业务状态的 UI 组件；**已统一为只用 Tv 版本**，不再保留 mobile material3 版本。
- `utils/` 只放无状态 Android 工具（`ApplicationUtils` / `IntentUtils` / `DrawableUtils` / `DisplayUtils` / `UIUtils`）。
- 持久化：**只有 DataStore(protobuf)** 存主屏固定图标（`icon_items.pb`，5 个槽位 `IconRepository.NUM_FIXED_ACTIVITY`）。Room 已删除，不要再引入第二套存储。

## 必须遵守的编码约定
- 协程：查 `PackageManager` / 文件系统一律 `Dispatchers.IO`；`Flow` 用 `flowOn(Dispatchers.IO)`；不要用 `Dispatchers.Default` 做 IO。
- Compose：状态一律 `collectAsStateWithLifecycle()`；组合期不得同步调用 `PackageManager`（用 `remember(key)` 预取）；不得在组合期写 state（用 `coerceIn` 之类的派生值）。
- 系统设置跳转必须走 `IntentUtils.launchActivityOrAction(...)`（组件不存在时回退到 `Settings.ACTION_*`），因为 OEM TV 的 Settings 组件名差异极大。
- D-pad 网格滚动统一用 `components/grid/GridFocus.kt` 的 `Modifier.followGridFocus(...)`，不要再在屏幕里复制粘贴按键/滚动逻辑。
- 尺寸用 `dp`、字号用 `sp`（`ui/theme/Dimen.kt`）；界面文案必须进 `values/strings.xml` + `values-zh-rCN/strings.xml`。

## 构建 / 验证命令（重要）
本机 **没有配置 `local.properties`**，编译需显式指定 SDK：
```bash
export ANDROID_HOME=/home/qian/android/sdk
export ANDROID_SDK_ROOT=/home/qian/android/sdk
cd /home/qian/IdeaProject/TV_Launcher_3
sh gradlew :app:assembleDebug          # 编译 + 打包
sh gradlew :app:lintDebug              # 静态检查（本仓库 lint 是硬门槛）
```
- SDK 位于 `/home/qian/android/sdk`，已装 `platforms;android-37.0` 与 `build-tools;37.0.0`（compileSdk=37 必需，默认只有 android-34）。
- `gradlew` 没有执行权限，必须用 `sh gradlew`。
- `gradle/libs.versions.toml` 里给 `androidTestImplementation` 也必须单独加 `platform(libs.androidx.compose.bom)`，否则 androidTest 类路径版本解析为空、`lintDebug` 直接失败。

## README 声明的功能 ↔ 实现现状
1. 主屏固定常用应用 — 已实现（长按菜单 移除/替换）。
2. 列出可启动应用并可管理 — 已实现（启动/卸载/详情/应用市场）；无搜索、无首字母索引、无手动排序。
3. 输入源屏幕（README 标注 Experimental）— **未实现**。`baseline_hdmi_port_24`、`baseline_serial_port_24`、`baseline_video_input_*`、`baseline_television_classic_24`、`baseline_settings_input_hdmi_24` 与 `R.string.signal_source` 是为该功能预留的资源，因此它们会一直产生 UnusedResources 告警，**这是有意保留，不要误删**。
4. 常用系统设置入口 — 已实现（10 个入口，均带 component→action 回退）。
