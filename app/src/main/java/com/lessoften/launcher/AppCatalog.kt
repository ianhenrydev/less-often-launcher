package com.lessoften.launcher

import android.content.*
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.text.Collator

data class AppEntry(val component: ComponentName, val label: String, val bucket: Bucket) {
    val packageName: String get() = component.packageName
}
class AppCatalog(private val context: Context, private val policy: BucketPolicy) {
    private val icons = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val iconGeneration = java.util.concurrent.atomic.AtomicInteger()
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val listeners = mutableSetOf<() -> Unit>()
    @Volatile var entries: List<AppEntry> = emptyList(); private set
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }
    init { launcher.registerCallback(callback, android.os.Handler(context.mainLooper)); refresh() }
    fun listen(listener: () -> Unit) { listeners.add(listener) }
    fun unlisten(listener: () -> Unit) { listeners.remove(listener) }
    fun refresh() {
        iconGeneration.incrementAndGet()
        icons.clear()
        entries = launcher.getActivityList(null, Process.myUserHandle()).filter { it.componentName.packageName != context.packageName }
            .map { activity ->
                val configured = policy.configured(activity.componentName.packageName)
                // Primary uses the approved short home labels. All other entries use installed labels.
                AppEntry(activity.componentName, if (configured?.bucket == Bucket.Primary) configured.name else activity.label.toString(),
                    policy.bucket(activity.componentName.packageName))
            }.distinctBy { it.component }
        listeners.toList().forEach { it() }
    }
    fun icon(entry: AppEntry, grayscale: Boolean): Bitmap? {
        val generation = iconGeneration.get()
        val key = entry.component.flattenToString() + ":" + grayscale
        icons[key]?.let { return it }
        return runCatching {
            val info = launcher.getActivityList(entry.packageName, Process.myUserHandle())
                .first { it.componentName == entry.component }
            val drawable = info.getIcon(context.resources.displayMetrics.densityDpi)
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val original = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(original))
            val result = if (grayscale) Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
                Canvas(it).drawBitmap(original, 0f, 0f, paint)
            } else original
            if (iconGeneration.get() == generation) icons[key] = result
            result
        }.getOrNull()
    }
    fun isLaunchable(pkg: String) = entries.any { it.packageName == pkg }
    fun list(bucket: Bucket): List<AppEntry> {
        val apps = entries.filter { it.bucket == bucket }
        return if (bucket == Bucket.Primary) policy.entries.filter { it.bucket == bucket }.flatMap { configured ->
            apps.filter { it.packageName == configured.packageName }.sortedBy { it.component.flattenToString() }
        } else apps.sortedWith { a, b ->
            Collator.getInstance().compare(a.label, b.label).takeIf { it != 0 }
                ?: a.component.flattenToString().compareTo(b.component.flattenToString())
        }
    }
    fun missing() = policy.entries.filter { configured -> entries.none { it.packageName == configured.packageName } }
    fun launch(entry: AppEntry) {
        // Re-resolve on every launch; stale or removed components never become a crashing target.
        if (!launcher.isActivityEnabled(entry.component, Process.myUserHandle())) { refresh(); error("App is no longer available") }
        launcher.startMainActivity(entry.component, Process.myUserHandle(), null, null)
    }
}
