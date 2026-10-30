package com.lessoften.probe

import android.app.*
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.*
import android.net.Uri
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray

/** Uses actual key events, never a grant endpoint, modified production duration, or private preferences. */
class ProbeInstrumentation : Instrumentation() {
    private lateinit var automation: UiAutomation
    private var mode = "smoke"
    private val launcher = "com.lessoften.launcher"
    private val probe = "com.lessoften.probe"
    private var grantRequestedAt = 0L
    private var grantObservedAt = 0L
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode") ?: "smoke"; start() }
    override fun onStart() {
        automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        @Suppress("DEPRECATION")
        val wakeLock = targetContext.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "$probe:acceptance")
        try {
            check(!targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock the Pixel before interactive device tests" }
            wakeLock.acquire(15 * 60_000L)
            check(shell("settings get secure enabled_accessibility_services").contains("$launcher/")) { "Enable Less Often accessibility first" }
            when (mode) {
                "display" -> displayRoundTrip()
                "notification" -> notification()
                "launcher" -> launcherNavigation()
                "color-transitions" -> colorTransitions()
                else -> smoke()
            }
            finish(0, Bundle().apply { putString("stream", "PASS: $mode\n") })
        } catch (e: Throwable) {
            report("Visible window metadata: " + automation.windows.joinToString { "type=${it.type},focused=${it.isFocused},package=${it.root?.packageName}" })
            key(KeyEvent.KEYCODE_HOME)
            finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}\n${e.stackTraceToString()}\n") })
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
    private fun report(message: String) { sendStatus(0, Bundle().apply { putString("stream", "$message\n") }) }
    private fun shell(command: String) = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
    }
    private fun nodes(pkg: String): List<AccessibilityNodeInfo> {
        fun visit(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
            (0 until node.childCount).flatMap { i -> node.getChild(i)?.let { visit(it) } ?: emptyList() }
        return automation.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pkg }.flatMap { visit(it) }
    }
    private fun waitUntil(message: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) { if (condition()) return; SystemClock.sleep(100) }
        error(message)
    }
    private fun isGate() = nodes(launcher).any { it.text?.toString() == "Open for 10 minutes" }
    private fun openDestination(value: String) {
        shell("am start -a android.intent.action.VIEW -d $value -n $probe/.ProbeActivity")
    }
    private fun key(code: Int) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) {
            shell("input keyevent $code")
            return
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            automation.injectInputEvent(KeyEvent(time, SystemClock.uptimeMillis(), action, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD), true)
        }
    }
    private fun quote(): String {
        val array = JSONArray(context.assets.open("quotes.json").bufferedReader().use { it.readText() })
        val texts = (0 until array.length()).map { array.getJSONObject(it).getString("text") }
        return nodes(launcher).firstNotNullOfOrNull { node -> node.text?.toString()?.takeIf { it in texts } } ?: error("No quote")
    }
    private fun typeQuote(text: String) {
        val input = nodes(launcher).first { it.className?.toString()?.endsWith("EditText") == true }
        check(!input.performAction(AccessibilityNodeInfo.ACTION_PASTE)) { "Paste unexpectedly accepted" }
        check(!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })) { "Accessibility insertion unexpectedly accepted" }
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: error("Cannot generate key events")
        events.forEach { automation.injectInputEvent(it, true) }
        check(nodes(launcher).first { it.className?.toString()?.endsWith("EditText") == true }.text.toString() == text) { "Typed text was not retained" }
    }
    private fun completeQuote() {
        typeQuote(quote())
        grantRequestedAt = SystemClock.elapsedRealtime()
        check(nodes(launcher).first { it.text?.toString() == "Open for 10 minutes" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitUntil("Gate did not dismiss after correct typing") { !isGate() }
        grantObservedAt = SystemClock.elapsedRealtime()
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = android.graphics.Rect(); node.getBoundsInScreen(bounds)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }
    private fun keyboard() = shell("dumpsys input_method").lineSequence().any { it.trim() == "mInputShown=true" }
    private fun clickText(text: String) {
        var node = nodes(launcher).firstOrNull { it.text?.toString() == text } ?: error("Launcher text missing: $text")
        while (!node.isClickable) node = node.parent ?: error("Not clickable: $text")
        tap(node)
    }
    private fun launcherNavigation() {
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Primary Home not shown") { nodes(launcher).any { it.text?.toString() == "Phone" } }
        val primary = listOf("Phone", "Messages", "Camera", "Maps", "Clock", "Settings")
        val visible = nodes(launcher).mapNotNull { it.text?.toString() }.filter { it in primary }.distinct()
        check(visible == primary) { "Wrong Home entry order: $visible" }
        clickText("Settings")
        waitUntil("Primary Settings did not launch directly") { automation.windows.any {
            it.root?.packageName?.toString() in setOf("com.android.settings", "com.google.android.settings.intelligence")
        } }
        check(!isGate())
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home did not return") { nodes(launcher).any { it.text?.toString() == "Secondary" } }
        clickText("Secondary")
        waitUntil("Secondary drawer missing") { nodes(launcher).any { it.text?.toString() == "Home" } && nodes(launcher).none { it.text?.toString() == "Phone" } }
        clickText("Calculator")
        waitUntil("Secondary Calculator did not launch directly") { automation.windows.any { it.root?.packageName?.toString() == "com.google.android.calculator" } }
        check(!isGate())
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home did not return after Calculator") { nodes(launcher).any { it.text?.toString() == "Restricted" } }
        clickText("Restricted")
        waitUntil("Restricted drawer missing Chrome") { nodes(launcher).any { it.text?.toString() == "Chrome" } }
        clickText("Chrome")
        waitUntil("Launcher-originated Chrome challenge missing") { isGate() }
        check(automation.windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            .none { it.root?.packageName?.toString() == "com.android.chrome" }) { "Chrome launched before the quote" }
        val previous = quote()
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents("partial".toCharArray())!!
        events.forEach { automation.injectInputEvent(it, true) }
        if (keyboard()) { key(KeyEvent.KEYCODE_BACK); waitUntil("IME did not close") { !keyboard() } }
        key(KeyEvent.KEYCODE_BACK)
        waitUntil("Back did not cancel challenge") { !isGate() }
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home not available after Back") { nodes(launcher).any { it.text?.toString() == "Restricted" } }
        clickText("Restricted")
        waitUntil("Restricted drawer did not reopen") { nodes(launcher).any { it.text?.toString() == "Chrome" } }
        clickText("Chrome")
        waitUntil("Chrome did not challenge again") { isGate() }
        check(quote() != previous)
        val text = nodes(launcher).first { it.className?.toString()?.endsWith("EditText") == true }.text?.toString()
        check(text.isNullOrEmpty() || text == "Type the quote") { "Partial input was restored" }
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home did not cancel prelaunch gate") { !isGate() }
        report("PASS: exact Home order; immediate Primary/Secondary launch; separate drawers; prelaunch challenge; Back/Home cancellation; partial input discarded; quote differs")
    }
    private fun notification() {
        key(KeyEvent.KEYCODE_HOME)
        val manager = targetContext.getSystemService(NotificationManager::class.java)
        ProbeNotifications.post(targetContext)
        waitUntil("Native notification missing while package locked") { manager.activeNotifications.any { it.id == 42 } }
        val native = manager.activeNotifications.first { it.id == 42 }.notification
        check(native.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "Destination 42; no personal data")
        // Exercise the real native inline-action PendingIntent with a synthetic RemoteInput response.
        val reply = native.actions.first { it.title.toString() == "Reply test" }
        val result = Intent()
        RemoteInput.addResultsToIntent(reply.remoteInputs, result, Bundle().apply { putCharSequence("reply", "synthetic test") })
        reply.actionIntent.send(targetContext, 0, result)
        waitUntil("Native inline reply did not complete") {
            manager.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "Inline reply received without opening the app" }
        }
        check(!isGate()) { "Background inline reply opened an unexpected gate" }
        report("PASS: native notification persists with preview; native inline-action PendingIntent works while locked without opening an app")
        ProbeNotifications.post(targetContext)
        check(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
        fun ownNotification(): AccessibilityNodeInfo? = (automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow))
            .flatMap { it.findAccessibilityNodeInfosByText("Less Often synthetic notification") + it.findAccessibilityNodeInfosByText("Less Often Test Probe") }.firstOrNull()
        val shadeUntil = SystemClock.elapsedRealtime() + 4000
        while (ownNotification() == null && SystemClock.elapsedRealtime() < shadeUntil) SystemClock.sleep(100)
        val found = ownNotification()
        if (found != null) {
            var row: AccessibilityNodeInfo = found
            while (!row.isClickable) row = row.parent ?: error("Native notification row is not clickable")
            check(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            report("Native notification row tapped in system shade")
        } else {
            key(KeyEvent.KEYCODE_HOME)
            report("NOTE: shade text unavailable to automation; executing this notification's actual content PendingIntent. Literal tray-tap check remains manual.")
            native.contentIntent.send(
                targetContext, 0, null, null, null, null,
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle())
        }
        waitUntil("Notification activity was not gated") { isGate() }
        val original = quote()
        val input = nodes(launcher).first { it.className?.toString()?.endsWith("EditText") == true }
        tap(input)
        waitUntil("Gboard did not open for the overlay") { keyboard() }
        check(isGate() && quote() == original) { "IME opening canceled the attempt" }
        key(KeyEvent.KEYCODE_BACK)
        waitUntil("Back did not hide the keyboard") { !keyboard() }
        check(isGate() && quote() == original) { "IME closing canceled the attempt" }
        tap(nodes(launcher).first { it.className?.toString()?.endsWith("EditText") == true })
        waitUntil("IME did not reopen") { keyboard() }
        val wrong = original.removeSuffix(".")
        typeQuote(wrong)
        check(nodes(launcher).first { it.text?.toString() == "Open for 10 minutes" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        check(isGate()) { "Incorrect punctuation unexpectedly granted access" }
        repeat(wrong.length) { key(KeyEvent.KEYCODE_DEL) }
        val varied = "  " + original.uppercase(java.util.Locale.ROOT).replace(" ", "  ") + "  "
        typeQuote(varied)
        check(nodes(launcher).first { it.text?.toString() == "Open for 10 minutes" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitUntil("Correct case/space variation was rejected") { !isGate() }
        waitUntil("Native notification destination was discarded") {
            nodes(probe).any { it.text?.toString() == "Destination: lessoften-test://notification/42" }
        }
        report("PASS: native notification content destination gated and preserved; IME opens/closes without canceling; punctuation rejected; case/extra spaces accepted")
        shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $probe/.OtherEntry")
        waitUntil("Alias did not share package access") { nodes(probe).any { it.text?.toString() == "Destination: generic-home" } }
        check(!isGate())
        report("PASS: separate launcher alias shares the same package window")
        key(KeyEvent.KEYCODE_HOME)
        // Leave only synthetic content foregrounded with its window keep-awake flag between stages.
        if (mode == "notification") openDestination("lessoften-test://ready/next-stage")
        manager.cancel(42)
    }
    private fun displayRoundTrip() {
        // Ordinary app transitions exercise the production journal; never mutate secure values in the test.
        val before = listOf("accessibility_display_daltonizer", "accessibility_display_daltonizer_enabled").associateWith {
            shell("settings get secure $it").trim()
        }
        val component = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.sleeperbot")
            .lineSequence().map { it.trim() }.lastOrNull { it.startsWith("com.sleeperbot/") } ?: error("Sleeper launcher entry is missing")
        val launch = shell("am start -W -n $component")
        check(!launch.contains("Error:")) { "Sleeper test launch failed: $launch" }
        waitUntil("Sleeper was not gated") { isGate() }
        check(shell("settings get secure accessibility_display_daltonizer").trim() == "0")
        check(shell("settings get secure accessibility_display_daltonizer_enabled").trim() == "1")
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home did not restore baseline") { before.all { (key, value) -> shell("settings get secure $key").trim() == value } }
        report("PASS: Sleeper external gate; grayscale settings applied; Home restored exact observed baseline")
    }
    private fun colorTransitions() {
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home not available for baseline") { nodes(launcher).any { it.text?.toString() == "Phone" } }
        SystemClock.sleep(600)
        val keys = listOf("accessibility_display_daltonizer", "accessibility_display_daltonizer_enabled")
        val baseline = keys.associateWith { android.provider.Settings.Secure.getString(targetContext.contentResolver, it) }
        fun accessRemaining(): Long {
            // Read-only check of this disposable package's deadline, never a grant/write endpoint.
            val xml = shell("run-as $launcher cat shared_prefs/runtime.xml")
            val record = Regex("<string name=\"session:${Regex.escape(probe)}\">([^<]+)</string>").find(xml)?.groupValues?.get(1)?.split('|') ?: return 0
            val boot = android.provider.Settings.Global.getInt(targetContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1).toString()
            if (record.firstOrNull() != boot) return 0
            return ((record.getOrNull(1)?.toLongOrNull() ?: 0) - SystemClock.elapsedRealtime()).takeIf { it in 1..600_000 } ?: 0
        }
        if (accessRemaining() in 1..30_000) SystemClock.sleep(accessRemaining() + 100)
        val locked = accessRemaining() == 0L
        openDestination("lessoften-test://color/42")
        if (locked) { waitUntil("Locked probe was not gated") { isGate() }; completeQuote() }
        waitUntil("Probe was not actually unlocked at its requested destination") {
            !isGate() && nodes(probe).any { it.text?.toString() == "Destination: lessoften-test://color/42" }
        }
        check(accessRemaining() > 30_000) { "A valid session was not committed for the transition test" }
        waitUntil("Grayscale did not activate") { android.provider.Settings.Secure.getString(targetContext.contentResolver, keys[1]) == "1" }
        val worker = HandlerThread("ColorTransitionObserver").apply { start() }
        val monitoring = java.util.concurrent.atomic.AtomicBoolean(false)
        val changes = java.util.concurrent.atomic.AtomicInteger(0)
        val colorSamples = java.util.concurrent.atomic.AtomicInteger(0)
        val observer = object : android.database.ContentObserver(Handler(worker.looper)) {
            override fun onChange(selfChange: Boolean) {
                if (monitoring.get()) {
                    changes.incrementAndGet()
                    if (android.provider.Settings.Secure.getString(targetContext.contentResolver, keys[1]) != "1") colorSamples.incrementAndGet()
                }
            }
        }
        targetContext.contentResolver.registerContentObserver(android.provider.Settings.Secure.getUriFor(keys[1]), false, observer)
        try {
            SystemClock.sleep(300)
            monitoring.set(true)
            shell("am start -W -n $probe/.ProbePageActivity --ei page 1")
            waitUntil("First distinct activity did not open") { nodes(probe).any { it.text?.toString() == "Activity page: 1" } }
            for (page in 2..16) {
                check(!isGate()) { "Probe session expired or locked before activity $page; retry with a fresh session" }
                check(nodes(probe).first { it.text?.toString()?.equals("Next activity", ignoreCase = true) == true }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitUntil("Activity $page did not open") { nodes(probe).any { it.text?.toString() == "Activity page: $page" } }
                SystemClock.sleep(70)
            }
            repeat(16) { key(KeyEvent.KEYCODE_BACK); SystemClock.sleep(70) }
            waitUntil("Back stack did not preserve the original destination") { nodes(probe).any { it.text?.toString() == "Destination: lessoften-test://color/42" } }
            SystemClock.sleep(300)
            monitoring.set(false)
            check(changes.get() == 0) { "Grayscale setting changed ${changes.get()} times during 32 same-package activity transitions; ${colorSamples.get()} callbacks observed color restored" }
            report("PASS: 32 actual activity transitions with zero grayscale-setting writes and original destination preserved")
        } finally {
            monitoring.set(false)
            targetContext.contentResolver.unregisterContentObserver(observer)
            worker.quitSafely()
            key(KeyEvent.KEYCODE_HOME)
        }
        waitUntil("Home did not restore the original display state") { baseline.all { (key, value) -> android.provider.Settings.Secure.getString(targetContext.contentResolver, key) == value } }
        report("PASS: Home restored both exact baseline settings after transition test")
    }
    private fun smoke() {
        val started = SystemClock.elapsedRealtime()
        openDestination("lessoften-test://deep-link/42")
        waitUntil("Unknown launchable app was not gated") { isGate() }
        report("Gate observed within ${SystemClock.elapsedRealtime() - started} ms of test launch request (includes app startup)")
        val first = quote()
        key(KeyEvent.KEYCODE_HOME)
        waitUntil("Home left an actionable gate") { !isGate() }
        openDestination("lessoften-test://deep-link/42")
        waitUntil("Canceled attempt did not gate again") { isGate() }
        check(quote() != first) { "Canceled quote repeated" }
        completeQuote()
        waitUntil("External destination was discarded") { nodes(probe).any { it.text?.toString() == "Destination: lessoften-test://deep-link/42" } }
        key(KeyEvent.KEYCODE_HOME)
        openDestination("lessoften-test://second/43")
        waitUntil("Unlocked app did not reopen at second destination") { nodes(probe).any { it.text?.toString() == "Destination: lessoften-test://second/43" } }
        check(!isGate())
        report("PASS: unknown app default, cancellation/nonrepeat, clipboard restrictions, typed completion, deep-link destination, valid-window reopen")
        if (mode == "ten-minute") {
            while (SystemClock.elapsedRealtime() - grantObservedAt < 602_000) {
                if (isGate()) break
                SystemClock.sleep(200)
            }
            check(isGate()) { "Foreground did not gate at real 600-second expiry" }
            val observed = SystemClock.elapsedRealtime()
            check(observed - grantRequestedAt >= 600_000) { "Gate appeared early" }
            check(observed - grantObservedAt <= 602_000) { "Expiry was late or reopening extended the session" }
            report("PASS: real foreground expiry without another launch; gate observed ${observed - grantRequestedAt} ms after completion click (grant-observation bracket ${grantObservedAt - grantRequestedAt} ms)")
        }
        key(KeyEvent.KEYCODE_HOME)
    }
}
