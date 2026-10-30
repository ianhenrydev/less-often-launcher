package com.lessoften.launcher

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/** All transitions, commits, display writes and overlay callbacks are serialized on the main looper. */
class GateService : AccessibilityService() {
    companion object { var current: GateService? = null; private set }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var gate: GateView? = null
    private var attempt: Attempt? = null
    private var observed: String? = null
    private var interrupted = true
    private var windowsUnavailable = false
    private var unavailableSince: Long? = null
    private var deviceAvailable = false
    private var displayUncertain = true
    private lateinit var displayContinuity: DisplayContinuity
    private data class Attempt(val pkg: String, val entry: AppEntry?)
    private data class Window(val type: Int, val pkg: String?, val layer: Int, val focused: Boolean, val active: Boolean)
    private val deadline = Runnable { reconcile() }
    private val colorDeadline = Runnable { reconcile() }
    private val poll = object : Runnable {
        override fun run() { reconcile(); handler.postDelayed(this, 500) }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                cancel(); observed = null; displayContinuity.reset(); handler.removeCallbacks(colorDeadline); restore()
            }
            else handler.post { reconcile() }
        }
    }
    private val colorObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { handler.post { reconcile() } }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        displayContinuity = DisplayContinuity(initiallyRestricted = state.display.active())
        current = this
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, RECEIVER_NOT_EXPORTED)
        DisplayJournal.transform.keys.forEach { contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, colorObserver) }
        handler.post(poll)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { reconcile() }
    private fun snapshot(): List<Window> = windows.map { window ->
        // Read only the root's package metadata. Never traverse nodes or request screen text.
        val root = window.root
        val pkg = root?.packageName?.toString()
        @Suppress("DEPRECATION") root?.recycle()
        Window(window.type, pkg, window.layer, window.isFocused, window.isActive)
    }
    private fun isRestricted(pkg: String): Boolean = pkg != packageName &&
        state.policy.bucket(pkg) == Bucket.Restricted &&
        (state.policy.configured(pkg) != null || state.catalog.isLaunchable(pkg))

    private fun readForeground(): String? {
        val keyguard = getSystemService(KeyguardManager::class.java)
        deviceAvailable = getSystemService(PowerManager::class.java).isInteractive && !keyguard.isKeyguardLocked
        if (!deviceAvailable) {
            windowsUnavailable = false
            interrupted = true; return null
        }
        val all = snapshot()
        val application = all.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedWith(compareByDescending<Window> { it.focused || it.active }.thenByDescending { it.layer })
            .firstOrNull()
        // IME and our accessibility overlay do not replace the underlying application.
        val systemInterruption = all.any {
            (it.focused || it.active) && it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                it.type != AccessibilityWindowInfo.TYPE_APPLICATION
        }
        val pkg = application?.pkg
        windowsUnavailable = pkg == null && !systemInterruption
        val infrastructure = pkg != null && pkg != packageName && state.policy.configured(pkg) == null && !state.catalog.isLaunchable(pkg)
        // An inactive Home/background window exposed between two activities is not a confirmed exit.
        // Restricted windows still take precedence in reconcile, including under our gate or the IME.
        displayUncertain = pkg == null || infrastructure || (!application.focused && !application.active)
        interrupted = systemInterruption || infrastructure || pkg == null
        return pkg
    }
    private fun reconcile() {
        if (current !== this) return
        try {
            observed = readForeground()
            if (windowsUnavailable) {
                if (unavailableSince == null) unavailableSince = SystemClock.elapsedRealtime()
                if (SystemClock.elapsedRealtime() - requireNotNull(unavailableSince) >= 2000)
                    state.failure = "Foreground windows are unavailable. Enforcement is not healthy; reconnect accessibility in setup."
            } else {
                unavailableSince = null
                if (state.failure?.startsWith("Foreground windows") == true) state.failure = null
            }
            handler.removeCallbacks(deadline)
            val pkg = observed
            val restricted = pkg != null && isRestricted(pkg)
            val displayContext = when {
                !deviceAvailable -> DisplayContext.Inactive
                restricted -> DisplayContext.Restricted
                displayUncertain -> DisplayContext.Uncertain
                else -> DisplayContext.Permitted
            }
            val now = SystemClock.elapsedRealtime()
            handler.removeCallbacks(colorDeadline)
            val grayscale = displayContinuity.useGrayscale(displayContext, now)
            displayContinuity.recheckDelay(now)?.let { handler.postDelayed(colorDeadline, it) }
            if (grayscale) {
                if (state.securePermission()) {
                    state.display.apply()
                    if (state.display.problem == null && (state.failure?.startsWith("Secure-settings") == true ||
                            state.failure?.startsWith("Enforcement") == true)) state.failure = null
                }
                else state.failure = "Secure-settings permission was revoked. Grayscale is inactive; use setup to recover."
            } else restore()
            if (interrupted) { cancel(); return }
            val active = attempt
            if (active != null) {
                val expected = if (active.entry != null) packageName else active.pkg
                if (pkg != expected) cancel()
                else return
            }
            if (restricted) {
                val remaining = state.sessions.remaining(pkg)
                if (remaining == 0L) show(Attempt(pkg, null))
                else handler.postDelayed(deadline, remaining)
            }
        } catch (e: Exception) {
            state.failure = "Enforcement needs attention: ${e.message}"
            cancel()
            performGlobalAction(GLOBAL_ACTION_HOME)
            // Keep Phone, Home, Settings and safety UI reachable on platform failure.
        }
    }
    fun requestLaunch(entry: AppEntry) {
        try {
            if (state.capabilityProblem() != null) { openSetup(); return }
            if (entry.bucket != Bucket.Restricted || state.sessions.remaining(entry.packageName) > 0) state.catalog.launch(entry)
            else { cancel(); show(Attempt(entry.packageName, entry)) }
        } catch (e: Exception) { state.failure = e.message; openSetup() }
    }
    fun onHomeRequested() {
        cancel(); displayContinuity.reset(); handler.removeCallbacks(colorDeadline); restore()
    }
    fun reconcileNow() { reconcile() }
    private fun show(target: Attempt) {
        if (attempt != null) return
        val quote = state.quotes.next() // Persist selection even if attachment fails or is canceled.
        val label = target.entry?.label ?: state.catalog.entries.firstOrNull { it.packageName == target.pkg }?.label ?: target.pkg
        val view = GateView(this, label, quote, { input -> complete(input) }, { goHome() })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE
        ).apply {
            title = "Less Often quote gate"
            setFitInsetsTypes(0) // Cover app pixels behind transparent bars; system bars stay above us.
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @Suppress("DEPRECATION") // Required for focusable native overlay; insets also protect IME space.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            gravity = Gravity.TOP or Gravity.START
        }
        attempt = target; gate = view
        try {
            wm.addView(view, params)
            // IME consumes Back first to hide itself; Back with the keyboard closed leaves the attempt.
            view.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goHome() }
            view.input.requestFocus()
        } catch (e: Exception) { cancel(); throw e }
    }
    private fun complete(input: String) {
        val target = attempt ?: return
        val view = gate ?: return
        try {
            val pkg = readForeground()
            if (interrupted || pkg != (if (target.entry != null) packageName else target.pkg)) { cancel(); return }
            if (!Quotes.matches(input, view.quote)) { view.showError("Match the words and punctuation. Capitalization and extra spaces do not matter."); return }
            state.sessions.grant(target.pkg) // Durable commit precedes overlay removal and target launch.
            cancel()
            if (target.entry != null) state.catalog.launch(target.entry)
            // For external entry, only remove the overlay: preserve the existing task and exact destination.
            handler.post { reconcile() }
        } catch (e: Exception) { view.showError("Access remains locked: ${e.message}"); state.failure = e.message }
    }
    private fun goHome() { cancel(); performGlobalAction(GLOBAL_ACTION_HOME) }
    private fun cancel() {
        val old = gate
        gate = null; attempt = null
        if (old != null) {
            old.input.text.clear()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(old.windowToken, 0)
            runCatching { wm.removeViewImmediate(old) }
        }
    }
    private fun restore() {
        if (state.display.active()) {
            val previousFailure = state.failure
            state.repairDisplay()
            if (state.failure == null) state.failure = previousFailure
        }
    }
    private fun openSetup() { startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    // This callback interrupts accessibility feedback, not service binding or the foreground app.
    override fun onInterrupt() { reconcile() }
    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }
    private fun shutdown() {
        if (current !== this) return
        current = null; handler.removeCallbacksAndMessages(null); cancel(); restore()
        runCatching { unregisterReceiver(screenReceiver) }
        contentResolver.unregisterContentObserver(colorObserver)
    }
}
