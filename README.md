# TV Launcher 3
## Basic information
TV Launcher 3 is a launcher application designed for Android TV, programmed by using Kotlin and Jetpack Compose.
It draws a wallpaper picked from the wallpaper tab as its background (falling back to a built-in one until the user picks one) and shows a clock in the top bar.
## Function
1. Direct access to your favourite applications on home screen by simply fix them on it. Long press a shortcut to replace or remove it.
2. Displays the launchable applications on your device — the activities that declare a `MAIN` / `CATEGORY_LAUNCHER` entry — and provides entrances to manage them: run, uninstall, app info and auto start. Selecting an app for auto start highlights it in red and makes the launcher start that app automatically once after the device boots; selecting it again clears the selection.
3. A built-in file browser: browse the internal storage and the removable volumes, open a file with its default application (an apk goes to the system installer), and copy, paste or delete a file or a folder from the menu key. Reaching the whole device requires the storage permission; on Android 11 and above that is the system's "All files access" permission.
4. Quick access to commonly used system settings: system settings, TV settings, WLAN, internet, Bluetooth, accessory, sound, display and screen saver.
5. A wallpaper tab: a single 4x3 grid showing the latest 12 Bing images (the source exposes only a ~15 image window and caps every request at 8, so a second page would hold 3 images at most — the tab therefore shows one page only). Pressing OK on a tile — or clicking it with a mouse — downloads the full resolution image to `Pictures/Wallpapers`, sets it as the system wallpaper and uses it as the launcher background.
