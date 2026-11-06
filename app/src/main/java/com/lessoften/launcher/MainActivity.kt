package com.lessoften.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat as JavaDateFormat
import java.util.Date

private val background = Color(0xFF171917)
private val surface = Color(0xFF232A22)
private val primary = Color(0xFFE6E8DF)
private val secondary = Color(0xFFA7AFA1)
private val accent = Color(0xFFA6B79A)
private data class MetricLine(val label: String, val value: String, val complete: Boolean, val observed: Boolean)

class MainActivity : ComponentActivity() {
    private var bucket by mutableStateOf(Bucket.Primary)
    private var details by mutableStateOf(false)
    private var revision by mutableIntStateOf(0)
    private var setupShown = false
    private var screenVisible by mutableStateOf(false)
    private val catalogChanged: () -> Unit = { revision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.catalog.listen(catalogChanged)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = background, surface = surface, primary = accent, onBackground = primary, onSurface = primary)) {
                BackHandler(details || bucket != Bucket.Primary) { if (details) details = false else bucket = Bucket.Primary }
                revision
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(screenVisible) { if (screenVisible) while (true) { now = System.currentTimeMillis(); delay(1_000) } }
                var usage by remember { mutableStateOf<UsageSnapshot?>(null) }
                LaunchedEffect(screenVisible) {
                    if (screenVisible) while (true) {
                        usage = withContext(Dispatchers.IO) { runCatching { UsageReader(this@MainActivity).read() }.getOrNull() }
                        delay(30_000)
                    }
                }
                if (details) Details(usage) { details = false }
                else if (bucket == Bucket.Primary) Home(now, usage)
                else Drawer(bucket, now)
            }
        }
    }
    private fun duration(millis: Long?): String {
        if (millis == null) return "—"
        if (millis in 1..59_999) return "<1m"
        val minutes = millis / 60_000
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }
    private fun observedDuration(millis: Long?, complete: Boolean): String =
        if (millis == null || (millis == 0L && !complete)) "—" else duration(millis)
    private fun observedCount(count: Int?, complete: Boolean): String =
        if (count == null || (count == 0 && !complete)) "—" else count.toString()
    private fun dayLabel(date: java.time.LocalDate): String = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    @Composable private fun Home(now: Long, usage: UsageSnapshot?) {
        Column(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text("Less Often", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(JavaDateFormat.getDateInstance(JavaDateFormat.FULL).format(Date(now)), color = secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text(DateFormat.getTimeFormat(this@MainActivity).format(Date(now)), color = primary, fontSize = 56.sp, lineHeight = 62.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(22.dp))
            val day = usage?.days?.firstOrNull()
            val available = usage?.access == true
            val screenValue = if (available) observedDuration(day?.screenMillis, day?.screenComplete == true) else "—"
            val unlockValue = if (available) observedCount(day?.unlocks, day?.screenComplete == true) else "—"
            val restrictedValue = if (available) observedDuration(day?.restrictedMillis, day?.restrictedComplete == true) else "—"
            val trackingMessage = if (usage == null) "Tracking incomplete" else if (!available) "Enable usage access" else if (day?.screenComplete != true || day.restrictedComplete != true) "Tracking incomplete" else ""
            fun spoken(value: String) = if (value == "—") "unavailable" else value
            val accessibilitySummary = "Today's activity. Screen time ${spoken(screenValue)}. Unlocks ${spoken(unlockValue)}. Restricted ${spoken(restrictedValue)}. $trackingMessage. Open usage details."
            Column(Modifier.fillMaxWidth().clickable { details = true }.padding(vertical = 8.dp).semantics { contentDescription = accessibilitySummary }) {
                Text("TODAY'S ACTIVITY", color = secondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("Screen time" to screenValue, "Unlocks" to unlockValue, "Restricted" to restrictedValue).forEach { (label, value) ->
                        Column(Modifier.weight(1f)) { Text(value, color = primary, fontSize = 22.sp, style = TextStyle(fontFeatureSettings = "tnum")); Text(label, color = secondary, fontSize = 12.sp, lineHeight = 16.sp) }
                    }
                }
                if (trackingMessage.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(trackingMessage, color = secondary, fontSize = 12.sp) }
                usage?.comparison?.let { Text(it.label(), color = secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            Spacer(Modifier.height(18.dp))
            val configured = state.policy.entries.filter { it.bucket == Bucket.Primary }
            configured.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { config ->
                        val entry = state.catalog.entries.filter { it.packageName == config.packageName }.minByOrNull { it.component.flattenToString() }
                        Column(Modifier.weight(1f).heightIn(min = 90.dp).background(surface, RoundedCornerShape(14.dp))
                            .clickable(enabled = entry != null) { entry?.let(::launch) }
                            .semantics { if (entry == null) { disabled(); contentDescription = "${config.name}, unavailable" } }
                            .padding(16.dp), verticalArrangement = Arrangement.Center) {
                            Symbol(config.name, if (entry == null) secondary else accent)
                            Spacer(Modifier.height(7.dp))
                            Text(config.name, color = if (entry == null) secondary else primary, fontSize = 17.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Bucket.Secondary, Bucket.Restricted).forEach { target ->
                    Text(target.name, Modifier.weight(1f).clickable { bucket = target }.padding(vertical = 16.dp), color = secondary, fontSize = 16.sp)
                }
            }
        }
    }
    @Composable private fun Symbol(name: String, color: Color) {
        val icon = when (name) {
            "Phone" -> R.drawable.symbol_phone
            "Messages" -> R.drawable.symbol_messages
            "Camera" -> R.drawable.symbol_camera
            "Maps" -> R.drawable.symbol_maps
            "Clock" -> R.drawable.symbol_clock
            else -> R.drawable.symbol_settings
        }
        Image(androidx.compose.ui.res.painterResource(icon), contentDescription = null,
            modifier = Modifier.size(25.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color))
    }
    @Composable private fun Drawer(which: Bucket, now: Long) {
        val apps = state.catalog.list(which)
        LazyColumn(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp)) {
            item { Text("Home", Modifier.clickable { bucket = Bucket.Primary }.padding(vertical = 18.dp), color = secondary); Text(which.name, fontSize = 26.sp, color = primary, modifier = Modifier.padding(bottom = 18.dp)) }
            val groups = apps.groupBy { it.label.trim().firstOrNull()?.let { c -> if (c.isLetter()) c.uppercaseChar().toString() else "#" } ?: "#" }
            groups.forEach { (heading, entries) ->
                item(key = "section:$heading") { Text(heading, color = accent, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                items(entries, key = { it.component.flattenToString() }) { app ->
                    Row(Modifier.fillMaxWidth().clickable { launch(app) }.heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, which == Bucket.Restricted)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(app.label, color = primary, fontSize = 17.sp)
                            if (which == Bucket.Restricted) { val remaining = remember(now, app.packageName) { state.sessions.remaining(app.packageName) }; Text(if (remaining > 0) "Open · ${(remaining + 59_999)/60_000}m left" else "Locked", color = secondary, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
    @Composable private fun AppIcon(app: AppEntry, gray: Boolean) {
        val bitmap by produceState<Bitmap?>(null, app.component, gray, revision) { value = withContext(Dispatchers.IO) { state.catalog.icon(app, gray) } }
        val icon = bitmap
        if (icon != null) Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp))
        else Box(Modifier.size(36.dp).background(surface, RoundedCornerShape(8.dp)))
    }
    @Composable private fun Details(usage: UsageSnapshot?, back: () -> Unit) {
        var selected by remember { mutableIntStateOf(0) }
        val day = usage?.days?.getOrNull(selected)
        Column(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
            Text("←  Home", Modifier.clickable { back() }.padding(vertical = 18.dp), color = secondary)
            Text(if (selected == 0) "Today" else day?.date?.let(::dayLabel) ?: "Today", fontSize = 26.sp, color = primary)
            Spacer(Modifier.height(20.dp))
            listOf(MetricLine("Screen time", observedDuration(day?.screenMillis, day?.screenComplete == true), day?.screenComplete == true, day?.screenMillis != null),
                MetricLine("Unlocks", observedCount(day?.unlocks, day?.screenComplete == true), day?.screenComplete == true, day?.unlocks != null),
                MetricLine("Restricted minutes", observedDuration(day?.restrictedMillis, day?.restrictedComplete == true), day?.restrictedComplete == true, day?.restrictedMillis != null),
                MetricLine("Notifications", observedCount(day?.notifications, day?.notificationsComplete == true), day?.notificationsComplete == true, day?.notifications != null),
                MetricLine("Restricted sessions opened", observedCount(day?.grants, day?.grantsComplete == true), day?.grantsComplete == true, day?.grants != null)).forEach { (label, value, complete, observed) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(label, color = primary); Text(if (!observed) "Unavailable" else if (complete && selected == 0) "Complete through now" else if (complete) "Complete" else "Partial", color = secondary, fontSize = 12.sp) }
                    Text(value, color = secondary)
                }
            }
            if (day?.screenComplete != true || day.restrictedComplete != true || day.notificationsComplete != true || day.grantsComplete != true)
                Text("Partial coverage: recorded values may omit activity. Dashes are unavailable.", color = secondary, modifier = Modifier.padding(vertical = 16.dp))
            val comparison = usage?.comparison
            val cutoff = comparison?.let { DateFormat.getTimeFormat(this@MainActivity).format(Date(java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + it.cutoffMinute * 60_000L)) }
            Text(if (comparison == null) "Building your baseline" else "${comparison.label()} · ${comparison.baselineDays} baseline days, through $cutoff", color = secondary)
            Text("Seven-day history", color = primary, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            usage?.days?.take(7)?.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().clickable { selected = index }.heightIn(min = 48.dp).padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (index == 0) "Today" else dayLabel(item.date), color = if (index == selected) accent else primary)
                    Text(observedDuration(item.screenMillis, item.screenComplete), color = secondary)
                }
            }
            Button(onClick = { startActivity(Intent(this@MainActivity, SetupActivity::class.java)) }, modifier = Modifier.padding(vertical = 20.dp)) { Text("Access and setup") }
        }
    }
    private fun launch(entry: AppEntry) {
        if (entry.bucket == Bucket.Restricted) {
            val service = GateService.current
            if (service == null) startActivity(Intent(this, SetupActivity::class.java)) else service.requestLaunch(entry)
        } else try { state.catalog.launch(entry) } catch (e: Exception) { state.failure = e.message; startActivity(Intent(this, SetupActivity::class.java)) }
    }
    override fun onResume() {
        super.onResume(); screenVisible = true; state.catalog.refresh()
        if (GateService.current == null) state.repairDisplay()
        if (!setupShown) android.os.Handler(mainLooper).postDelayed({
            if (screenVisible && !isFinishing && !setupShown && state.capabilityProblem() != null) {
                setupShown = true; startActivity(Intent(this, SetupActivity::class.java))
            }
        }, 1_500)
    }
    override fun onPause() { screenVisible = false; super.onPause() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); bucket = Bucket.Primary; details = false; GateService.current?.onHomeRequested() }
    override fun onDestroy() { state.catalog.unlisten(catalogChanged); super.onDestroy() }
}
